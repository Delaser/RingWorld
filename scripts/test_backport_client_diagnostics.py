import unittest
from pathlib import Path
import tempfile

from scripts.run_backport_client_diagnostics import cases, required_native_captures, build_identity_arguments, normalize_profile_options


class BackportClientRecipeTests(unittest.TestCase):
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
