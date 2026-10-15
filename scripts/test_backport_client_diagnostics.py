import unittest
from pathlib import Path
import tempfile
from unittest.mock import Mock, patch

from scripts.run_backport_client_diagnostics import cases, required_native_captures, build_identity_arguments, normalize_profile_options, validate_cache_observations, wait_for_native_exit, validate_cloud_cameras


class BackportClientRecipeTests(unittest.TestCase):
    def test_streaming_native_and_verifier_share_fabulous_policy(self):
        recipe = next(case for case in cases() if case.name == 'streaming')
        self.assertEqual('fabulous', recipe.properties['projectionGraphicsMode'])
        self.assertEqual(recipe.properties['projectionGraphicsMode'], recipe.gradle_properties['ProjectionGraphicsMode'])

    def test_cloud_camera_proof_rejects_occluded_or_displaced_native_views(self):
        lines = []
        for stage in range(6):
            height = 104 + (-16, 2, 16)[stage % 3]
            lines.append(f'[cloud-camera] stage={stage} mode={"FANCY" if stage < 3 else "FAST"} base=104 x=512.5 eyeY={height} z=0.5 expectedEyeY={height} serverVolumeClear=true clientEyeAir=true clientVolumeClear=true flying=true')
        valid = '\n'.join(lines)
        self.assertEqual(6, len(validate_cloud_cameras(valid)))
        for wrong in [valid.replace('clientEyeAir=true', 'clientEyeAir=false', 1),
                      valid.replace('eyeY=88 ', 'eyeY=88.2 ', 1),
                      valid.replace('mode=FAST', 'mode=FANCY', 1),
                      valid.replace('x=512.5', 'x=528.5', 1),
                      valid.replace('eyeY=88 ', 'eyeY=NaN ', 1), '\n'.join(lines[:-1])]:
            with self.assertRaises(RuntimeError): validate_cloud_cameras(wrong)

    def test_dead_capture_player_fails_immediately_without_waiting_for_exit(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'process.log'
            path.write_text('[projection-capture] result=false: capture player died before completion\n')
            process = Mock(); process.poll.return_value = None
            with patch('scripts.run_backport_client_diagnostics.time.sleep') as sleep:
                with self.assertRaisesRegex(RuntimeError, 'explicit failure before exit'):
                    wait_for_native_exit(process, path, 'CAPTURE COMPLETE')
            sleep.assert_not_called()
            process.wait.assert_not_called()

    def test_capture_success_without_normal_shutdown_times_out(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'process.log'
            path.write_text('CAPTURE COMPLETE\nSaving worlds\n')
            process = Mock(); process.poll.return_value = None
            with patch('scripts.run_backport_client_diagnostics.time.monotonic', side_effect=[0, 1, 121]), \
                    patch('scripts.run_backport_client_diagnostics.time.sleep'):
                with self.assertRaisesRegex(TimeoutError, 'normal client/server shutdown'):
                    wait_for_native_exit(process, path, 'CAPTURE COMPLETE')
            process.wait.assert_not_called()

    def test_native_exit_code_is_preserved_for_postprocessing(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'process.log'; path.write_text('CAPTURE COMPLETE\n')
            process = Mock(); process.poll.side_effect = [None, 17]; process.wait.return_value = 17
            with patch('scripts.run_backport_client_diagnostics.time.sleep'):
                self.assertEqual(17, wait_for_native_exit(process, path, 'CAPTURE COMPLETE'))

    def test_cache_proof_requires_both_rejection_and_positive_reuse_at_an_identical_revision(self):
        original = {'status': 'PASS', 'worldHash': '1', 'revision': 42, 'cells': 262144, 'contentFingerprint': '111'}
        foreign = original | {'contentFingerprint': '222'}
        report = {'schemaVersion': 1, 'normalDisconnects': 3, 'revision': 42, 'contentFingerprint': '111',
                  'metadataInstalls': [{'cacheReused': index == 2, 'presentBeforeStreaming': 262144 if index == 2 else 0,
                                       'clientContent': '111' if index == 2 else '0', 'serverContent': '111', 'revision': 42}
                                      for index in range(3)]}
        validate_cache_observations(report, original, foreign)
        # Initial cold metadata precedes the fully settled authoritative baseline.
        report['metadataInstalls'][0]['revision'] = 11
        report['metadataInstalls'][0]['serverContent'] = '333'
        validate_cache_observations(report, original, foreign)
        for wrong in [original, foreign | {'revision': 43}, foreign | {'status': 'PARTIAL'}]:
            with self.assertRaises(RuntimeError): validate_cache_observations(report, original, wrong)
        for key, wrong in [('cacheReused', True), ('presentBeforeStreaming', 1), ('clientContent', '111')]:
            import copy
            invalid = copy.deepcopy(report); invalid['metadataInstalls'][1][key] = wrong
            with self.assertRaises(RuntimeError): validate_cache_observations(invalid, original, foreign)
        invalid = copy.deepcopy(report); invalid['metadataInstalls'][1]['revision'] = 11
        with self.assertRaises(RuntimeError): validate_cache_observations(invalid, original, foreign)

    def test_first_run_normalization_preserves_fixture_graphics_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as directory:
            runtime = Path(directory)
            path = runtime / 'options.txt'
            path.write_text('guiScale:4\nrenderDistance:28\nmaxFps:60\nonboardAccessibility:true\n')
            normalize_profile_options(runtime)
            first = path.read_text()
            normalize_profile_options(runtime)
            self.assertEqual(first, path.read_text())
            self.assertTrue(first.startswith('guiScale:4\nrenderDistance:28\nmaxFps:60\n'))
            self.assertEqual(1, first.count('onboardAccessibility:false'))
            self.assertIn('tutorialStep:none\n', first)
            self.assertIn('pauseOnLostFocus:false\n', first)

    def test_unicode_build_identity_stays_on_the_native_argument_boundary(self):
        label = '1.4 development · 1.4.0-dev.1+mc1.21.1'
        recipes = {case.name: case for case in cases()}
        self.assertEqual(['-Dringworld.atlasUiExpectedBuildLabel=' + label],
                         build_identity_arguments(recipes['atlas-ui'], label))
        self.assertEqual([], build_identity_arguments(recipes['underwater'], label))

    def test_every_projection_environment_keeps_three_raw_windows_and_native_verifier(self):
        recipes = cases()
        self.assertEqual(len(recipes), len({case.name for case in recipes}))
        for environment in ('noon', 'dusk', 'night', 'rain'):
            recipe = next(case for case in recipes if case.name == 'production-' + environment)
            self.assertEqual((3, 3, 'medium', 'verifyProductionProjectionClient'),
                             (recipe.captures, recipe.raw_frames, recipe.master, recipe.verifier))
            self.assertEqual(environment, recipe.properties['projectionEnvironment'])
            self.assertEqual(environment, recipe.gradle_properties['ProjectionEnvironment'])

    def test_safe_small_matrix_lifecycle_and_new_native_controls_are_not_omitted(self):
        recipes = {case.name: case for case in cases()}
        for distance in (6, 12, 28):
            recipe = recipes['small-seam-rims-' + str(distance)]
            self.assertEqual(('small', 4, 1), (recipe.master, recipe.captures, recipe.raw_frames))
        for name in ('creation-ui', 'atlas-ui', 'map-compass', 'curved-objects',
                     'production-lifecycle', 'layout-switch', 'underwater', 'horizon',
                     'floating-atlas', 'walls', 'altitude', 'clouds', 'foliage', 'streaming',
                     'fabulous-noon', 'fabulous-rain'):
            self.assertIn(name, recipes)
        self.assertEqual(7, recipes['underwater'].captures)
        self.assertEqual('First', recipes['layout-switch'].properties['layoutSwitchFirst'])
        self.assertEqual('Second', recipes['layout-switch'].properties['layoutSwitchSecond'])

    def test_unverified_native_controls_require_every_named_image(self):
        for recipe in cases():
            if recipe.verifier is None:
                self.assertEqual(recipe.captures, len(required_native_captures(recipe)))


if __name__ == '__main__': unittest.main()
