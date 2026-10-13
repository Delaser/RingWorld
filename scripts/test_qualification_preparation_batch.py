"""One preparation/assets process must finish before unchanged native settles."""
import importlib
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from contextlib import ExitStack
from minecraft_qualification_model import Verdict


class PreparationBatchTest(unittest.TestCase):
    def test_both_runners_batch_tasks_before_settle_and_never_start_on_failure(self):
        for module, installed, settle, error in [
                ('run_gradle_multiplayer_qualification', '_verify_installed_candidates', '_post_prepare_settle', 'GradleMultiplayerError'),
                ('run_gradle_raid_qualification', '_verify_installed', '_phase_settle', 'GradleRaidError')]:
            runner = importlib.import_module(module)
            for verdict in [Verdict.PASS, Verdict.FAIL]:
                with self.subTest(module=module, verdict=verdict), tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
                    cell = {'loader': 'fabric', 'minecraft': {'version': '26.1'}, 'profile': {'server_port': 25566}}
                    paths = SimpleNamespace(run_directory=Path(directory), repository_root=Path(directory), gradle_home=Path(directory)/'home', run_id='probe')
                    prepared = SimpleNamespace(paths=paths, cell=cell)
                    for name in ['create_contained_directories', 'stage_gradle_distribution_zip', '_stage_loom_seed', '_configure_rcon']:
                        stack.enter_context(patch.object(runner, name))
                    stack.enter_context(patch.object(runner, '_timeout', return_value=900))
                    stack.enter_context(patch.object(runner, '_record', side_effect=lambda prepared, tasks, *args: tasks))
                    stack.enter_context(patch.object(runner, '_executed_record', return_value={}))
                    verify = stack.enter_context(patch.object(runner, installed, return_value=[]))
                    wait = stack.enter_context(patch.object(runner, settle, side_effect=RuntimeError('native settle boundary')))
                    if module.endswith('multiplayer_qualification'):
                        stack.enter_context(patch.object(runner, '_configure_client_frame_caps'))
                    execute = stack.enter_context(patch.object(runner, 'execute_command', return_value=SimpleNamespace(verdict=verdict)))
                    expected_error = RuntimeError if verdict is Verdict.PASS else getattr(runner, error)
                    with self.assertRaises(expected_error):
                        runner._execute(prepared, None, None, (), 120)
                    self.assertEqual(1, execute.call_count)
                    tasks = runner._tasks('fabric')
                    self.assertEqual((tasks['prepare'], tasks['assets']), execute.call_args.args[0])
                    self.assertEqual(verdict is Verdict.PASS, verify.called)
                    self.assertEqual(verdict is Verdict.PASS, wait.called)
                    if wait.called:self.assertEqual(120, wait.call_args.args[0])
