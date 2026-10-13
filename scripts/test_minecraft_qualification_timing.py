"""Timings describe real boundaries without double-counting concurrent clients."""
from pathlib import Path
import tempfile
import unittest
from minecraft_qualification_timing import span, summary, game_milestones, startup_observation


class QualificationTimingTest(unittest.TestCase):
    def test_process_union_not_sum_and_invalid_elapsed_is_ignored(self):
        commands = [{'started_at': '2026-10-13T00:00:00+00:00', 'elapsed_seconds': 20},
                    {'started_at': '2026-10-13T00:00:05+00:00', 'elapsed_seconds': 10}]
        self.assertEqual(20, span(commands))
        self.assertEqual(0, span([{'started_at': 'invalid', 'elapsed_seconds': 10}]))

    def test_resume_and_retries_are_separate_from_current_process_times(self):
        rows = [{'cell': 'a', 'fixture': 'raid', 'verdict': 'PASS', 'reused_from': {'path': 'old'},
                 'command': {'elapsed_seconds': 100}},
                {'cell': 'b', 'fixture': 'raid', 'verdict': 'FAIL', 'attempts': [
                    {'command': {'elapsed_seconds': 5}, 'retry_scheduled': True},
                    {'command': {'elapsed_seconds': 10}, 'retry_scheduled': False}]}]
        report = summary(rows, Path('/unused'))
        self.assertEqual(1, report['reused_fixture_count'])
        self.assertEqual(100, report['prior_command_seconds_skipped'])
        self.assertEqual(135, report['executed_seconds'])
        self.assertEqual(120, report['fixtures'][1]['retry_cooldown_seconds'])

    def test_setup_and_verification_are_separate_from_runtime(self):
        commands = [{'name': name, 'started_at': '2026-10-13T00:00:'+start+'+00:00',
                     'elapsed_seconds': duration} for name, start, duration in
                    [('prepare-and-assets', '00', 5), ('client-a', '05', 20),
                     ('client-b', '06', 18), ('verify', '25', 2)]]
        row = {'cell': 'a', 'fixture': 'multiplayer', 'verdict': 'PASS',
               'command': {'elapsed_seconds': 150}, 'child': {'commands': commands},
               'post_prepare_settle_seconds': 120}
        report = summary([row], Path('/unused'))['fixtures'][0]
        self.assertEqual(5, report['setup_assets_seconds'])
        self.assertEqual(20, report['runtime_processes_seconds'])
        self.assertEqual(2, report['verification_seconds'])
        self.assertEqual(123, report['unattributed_seconds'])
        self.assertEqual(120, report['configured_multiplayer_settle_seconds'])

    def test_game_milestones_cross_midnight_and_do_not_fake_missing_events(self):
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / 'game.log'
            log.write_text('[23:59:59] [Render thread/INFO] RingWorld terrain atlas ready: 0/100 cached cells\n'
                           '[00:00:05] [Render thread/INFO] Textured ring surface ready: mesh=terrain-height\n')
            self.assertEqual({'first_atlas_ready_to_detailed_seconds': 6}, game_milestones(log))
            log.write_text('No game markers\n')
            self.assertEqual({}, game_milestones(log))

    def test_startup_uses_recorded_local_offset_instead_of_review_host_timezone(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory);log = root / 'dist/qualification/start.log'
            log.parent.mkdir(parents=True)
            log.write_text('[14:00:12] [Render thread/INFO] (Minecraft) Initializing\n')
            command = {'name': 'client', 'stdout': str(log), 'elapsed_seconds': 50,
                       'started_at_local': '2026-10-13T14:00:00+02:00'}
            self.assertEqual(12, startup_observation(command, root)['first_game_log_delay_seconds'])
            log.write_text('[13Oct2026 14:00:12.500] [Render thread/INFO] [minecraft/Minecraft]: Initializing\n')
            self.assertEqual(12.5, startup_observation(command, root)['first_game_log_delay_seconds'])
            command.pop('started_at_local')
            self.assertEqual({}, startup_observation(command, root))
