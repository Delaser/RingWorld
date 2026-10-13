"""Real terminal/hash review and exact-key execution for resumable qualification."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from contextlib import ExitStack

from minecraft_nightly_resume import select_resume
from review_composite_nightly_evidence import CompositeEvidenceError

COMMIT = 'c' * 40
CANDIDATE = 'a' * 64
QUICK = 'b' * 64
MANIFEST = 'd' * 64
CELL = '26.1-neoforge'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class NightlyResumeTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.manifest = {'cells': [{'id': CELL, 'loader': 'neoforge', 'minecraft': {'version': '26.1'}, 'profile': {'id':'safe-small', 'evidence_directory':'dist/qualification/ringworld/26.1/neoforge'}}]}
        self.inventory = {'sha256': 'e' * 64, 'files': 2, 'bytes': 100}
        self.expected = {CELL: {'frozen_candidate_sha256': CANDIDATE, 'quick_terminal_sha256': QUICK}}

    def terminal(self, fixture='creation-ui', **changes):
        names = {'creation-ui': 'creation-settings-ui', 'raid': 'frozen-raid-seam'}
        exact = fixture == 'raid'
        data = {'verdict': 'PASS', 'cell': CELL, 'fixture': names[fixture],
                'loader': 'neoforge', 'minecraft': '26.1', 'source': {'commit': COMMIT},
                'claims': {'frozen_candidate_jar': exact},
                'frozen_candidate': {'sha256': CANDIDATE}, 'quick_evidence': {'sha256': QUICK}, **changes}
        p = self.root / (fixture + '.json');p.write_text(json.dumps(data))
        return p

    def row(self, fixture, verdict='PASS', **terminal_changes):
        refs = []
        if verdict == 'PASS':
            p = self.terminal(fixture, **terminal_changes)
            refs = [{'path': str(p), 'sha256': digest(p)}]
        return {'cell': CELL, 'fixture': fixture, 'verdict': verdict, 'terminal_evidence': refs,
                'command': {'elapsed_seconds': 60}}

    def report(self, rows, name='aggregate.json', **changes):
        p = self.root / name
        p.write_text(json.dumps({'format': 2, 'verdict': 'INCOMPLETE', 'quick_run_id': 'quick',
                'started_source_commit': COMMIT, 'manifest_sha256': MANIFEST,
                'production_world': {'inventory': self.inventory}, 'results': rows, **changes}))
        return p

    def select(self, reports, **changes):
        return select_resume(root=self.root, manifest=self.manifest, manifest_sha256=MANIFEST,
                quick_run_id='quick', source_commit=COMMIT, production_inventory=self.inventory,
                reports=reports, expected=self.expected, fixtures=['creation-ui', 'raid'], **changes)

    def test_partial_checkpoint_preserves_pass_and_selects_only_missing_key(self):
        result = self.select([self.report([self.row('creation-ui')])])
        self.assertEqual([(CELL, 'raid')], result['pending'])
        self.assertEqual(['creation-ui'], [r['fixture'] for r in result['preserved']])
        self.assertEqual([], result['blocked'])

    def test_failed_assertion_is_blocked_until_explicit_retry(self):
        p = self.report([self.row('creation-ui'), self.row('raid', 'FAIL')])
        self.assertEqual([], self.select([p])['pending'])
        self.assertEqual(['raid'], [r['fixture'] for r in self.select([p])['blocked']])
        self.assertEqual([(CELL, 'raid')], self.select([p], retry_failed=True)['pending'])

    def test_cumulative_checkpoints_do_not_duplicate_passes_or_erase_failure(self):
        rows = [self.row('creation-ui'), self.row('raid', 'FAIL')]
        first = self.report(rows, 'checkpoint-1.json')
        second = self.report([rows[0], self.row('raid', 'INCOMPLETE')], 'checkpoint-2.json')
        result = self.select([first, second])
        self.assertEqual(1, len(result['preserved']))
        self.assertEqual('FAIL', result['blocked'][0]['verdict'])
        self.assertEqual(2, len(result['references']))

    def test_changed_source_quick_manifest_world_and_legacy_reports_are_rejected(self):
        for changes in ({'started_source_commit': 'f' * 40}, {'quick_run_id': 'other'},
                        {'manifest_sha256': None}, {'manifest_sha256': 'f' * 64},
                        {'production_world': {'inventory': {}}}):
            with self.subTest(changes=changes), self.assertRaises(CompositeEvidenceError):
                self.select([self.report([], **changes)])

    def test_terminal_source_candidate_quick_loader_and_patch_are_bound(self):
        for changes in ({'source': {'commit': 'f' * 40}},
                        {'frozen_candidate': {'sha256': 'f' * 64}},
                        {'quick_evidence': {'sha256': 'f' * 64}},
                        {'loader': 'fabric'}, {'minecraft': '26.2'}):
            with self.subTest(changes=changes), self.assertRaises(CompositeEvidenceError):
                self.select([self.report([self.row('raid', **changes)])])

    def test_hash_changed_terminal_missing_capture_and_unknown_key_are_rejected(self):
        row = self.row('creation-ui');p = self.report([row])
        (self.root / 'creation-ui.json').write_text('{}')
        with self.assertRaisesRegex(CompositeEvidenceError, 'hash'):
            self.select([p])
        image = self.root / 'missing.png'
        row = self.row('creation-ui', captures=[{'path': str(image), 'sha256': 'f' * 64}])
        with self.assertRaisesRegex(CompositeEvidenceError, 'neither present nor retained'):
            self.select([self.report([row])])
        with self.assertRaisesRegex(CompositeEvidenceError, 'unknown'):
            self.select([self.report([{'cell': '26.2-fabric', 'fixture': 'raid', 'verdict': 'FAIL'}])])

    def test_symlink_and_ambiguous_replacement_cannot_reuse_evidence(self):
        p = self.report([self.row('creation-ui')]);link = self.root / 'link.json'
        try:
            link.symlink_to(p)
        except OSError:
            pass  # Windows without symlink privilege still exercises other gates.
        else:
            with self.assertRaises(CompositeEvidenceError):self.select([link])
        with self.assertRaisesRegex(CompositeEvidenceError, 'ambiguously'):
            self.select([p, self.report([self.row('creation-ui', 'FAIL')], 'different.json')])

    def test_resume_executor_launches_only_authorized_failed_key_and_labels_composite(self):
        import run_minecraft_nightly_matrix as runner
        from minecraft_qualification_model import Verdict
        rows = [self.row('creation-ui'), self.row('raid', 'FAIL')]
        report = self.report(rows)
        manifest = self.root / 'manifest.json';manifest.write_text('{}')
        # Resume manifest binds the actual selected file, not a label.
        self.manifest_hash = digest(manifest)
        raw = json.loads(report.read_text());raw['manifest_sha256'] = self.manifest_hash;report.write_text(json.dumps(raw))
        args = argparse.Namespace(manifest='manifest.json', quick_run_id='quick',
                gradle_dependency_cache=None, gradle_distribution_zip=None,
                resume_aggregate=[str(report)], retry_failed_fixtures=True)
        planned = {'cells': [CELL], 'fixtures': ['creation-ui', 'raid'],
                'production_world': {'inventory': self.inventory}, 'complete_matrix_selection': True,
                'multiplayer_cooldown_seconds': 120,
                'commands': [{'cell': CELL, 'fixture': f, 'argv': [f]} for f in ['creation-ui', 'raid']]}
        terminal = self.terminal('raid');ref = {'path': str(terminal), 'sha256': digest(terminal)}
        child = SimpleNamespace(stdout_log='stdout', stderr_log='stderr', reason=None,
                verdict=Verdict.PASS, argv=('raid',), return_code=0,
                started_at_utc='2026-10-13T00:00:00Z', elapsed_seconds=1)
        with ExitStack() as stack:
            for name, value in {'collect_source_provenance': SimpleNamespace(commit=COMMIT),
                    'validate_gradle_dependency_cache': None, 'validate_gradle_distribution_zip': None,
                    'load_manifest': self.manifest, '_expected_identities': self.expected,
                    'create_contained_directories': None, '_verify_terminals': (ref,),
                    '_retain_terminal_artifacts': (), 'write_terminal_report': None}.items():
                stack.enter_context(patch.object(runner, name, return_value=value))
            stack.enter_context(patch.object(runner, '_last_json', return_value={'verdict': 'PASS'}))
            execute = stack.enter_context(patch.object(runner, 'execute_command', return_value=child))
            result = runner.execute(args, planned, repository_root=self.root)
        self.assertEqual(1, execute.call_count)
        self.assertEqual(('raid',), execute.call_args.args[0].argv)
        self.assertEqual('COMPOSITE_COVERAGE_REVIEWED', result['verdict'])
        self.assertFalse(result['monolithic_pass'])
        self.assertEqual(1, result['timing_summary']['reused_fixture_count'])
