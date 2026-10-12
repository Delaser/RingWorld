"""A failed stage must leave all remaining release groups runnable."""
import argparse
import unittest
from run_release_full_matrix import run_groups


class ReleaseFullMatrixTest(unittest.TestCase):
    def test_failed_group_does_not_skip_remaining_groups_or_become_pass(self):
        calls = []
        def execute(arguments, manifest, quick):
            calls.append(quick)
            if quick == 'first':
                raise OSError('host unavailable for this group')
            return {'verdict': 'PASS', 'run_id': quick, 'started_source_commit': 'same-source'}
        arguments = argparse.Namespace(execute=True, quick_26_1='first', quick_26_2='second', quick_26_3='third')
        result = run_groups(arguments, executor=execute)
        self.assertEqual(['first', 'second', 'third'], calls)
        self.assertEqual(['FAIL', 'PASS', 'PASS'], [r['verdict'] for r in result['groups']])
        self.assertEqual('FAIL', result['verdict'])

    def test_dry_run_never_qualifies_and_full_pass_requires_every_group(self):
        arguments = argparse.Namespace(execute=False, quick_26_1='a', quick_26_2='b', quick_26_3='c')
        self.assertEqual('INCOMPLETE', run_groups(arguments, executor=lambda *a: {'verdict': 'PASS'})['verdict'])
        arguments.execute = True
        self.assertEqual('PASS', run_groups(arguments, executor=lambda *a: {
            'verdict': 'PASS', 'started_source_commit': 'same-source'})['verdict'])
        self.assertEqual('FAIL', run_groups(arguments, executor=lambda *a: {'verdict': 'INCOMPLETE'})['verdict'])

    def test_missing_or_mixed_source_never_qualifies(self):
        arguments = argparse.Namespace(execute=True, quick_26_1='a', quick_26_2='b', quick_26_3='c')
        for executor in (lambda *a: {'verdict': 'PASS'},
                         lambda arguments, manifest, quick: {
                             'verdict': 'PASS', 'started_source_commit': quick}):
            self.assertEqual('FAIL', run_groups(arguments, executor=executor)['verdict'])

    def test_every_group_checkpoints_without_claiming_pass(self):
        arguments = argparse.Namespace(execute=True, quick_26_1='a', quick_26_2='b', quick_26_3='c')
        checkpoints = []
        def execute(*args):
            raise ValueError('invalid preparation input')
        result = run_groups(arguments, executor=execute,
                            checkpoint=lambda report, count: checkpoints.append((report, count)))
        self.assertEqual([1, 2, 3], [count for _, count in checkpoints])
        self.assertTrue(all(report['verdict'] == 'INCOMPLETE' for report, _ in checkpoints))
        self.assertEqual([1, 2, 3], [len(report['groups']) for report, _ in checkpoints])
        self.assertEqual('FAIL', result['verdict'])
