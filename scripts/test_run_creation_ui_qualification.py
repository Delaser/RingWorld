#!/usr/bin/env python3

import unittest

from run_creation_ui_qualification import parser


class RunCreationUiQualificationTest(unittest.TestCase):
    def test_parser_requires_explicit_external_inputs(self):
        parsed = parser().parse_args([
            "--cell", "26.1-fabric",
            "--quick-run-id", "20260813T072608Z-b7c68e555818",
            "--prism-archive", "/tmp/prism.zip",
            "--java", "/tmp/jdk/bin/java",
        ])
        self.assertEqual("26.1-fabric", parsed.cell)
        self.assertEqual("/tmp/prism.zip", parsed.prism_archive)
        self.assertEqual("/tmp/jdk/bin/java", parsed.java)

    def test_parser_has_no_live_or_user_profile_option(self):
        destinations = {action.dest for action in parser()._actions}
        self.assertNotIn("prism_data", destinations)
        self.assertNotIn("world", destinations)
        self.assertNotIn("server", destinations)


if __name__ == "__main__":
    unittest.main()
