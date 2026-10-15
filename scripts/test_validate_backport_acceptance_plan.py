"""Prevent missing coverage, stale source and false readiness in the port plan."""
import copy
import json
from pathlib import Path
import subprocess
import sys
import unittest

from scripts.validate_backport_acceptance_plan import DEFAULT_PLAN, PlanError, validate


class BackportAcceptancePlanTests(unittest.TestCase):
    def setUp(self):
        self.plan = json.loads(DEFAULT_PLAN.read_text(encoding='utf-8'))

    def test_complete_plan_is_explicitly_not_runtime_qualification(self):
        report = validate(self.plan)
        self.assertEqual('PLAN_VALID_NOT_QUALIFIED', report['status'])
        self.assertEqual((40, 28, 68), tuple(report[k] for k in
                         ('coreFixtureKeys', 'extensionKeys', 'expectedCellFamilyKeys')))
        self.assertFalse(report['runtimeExecutionPerformed'])

    def test_each_missing_core_or_feature_family_is_rejected(self):
        for key in ('coreFixtures', 'requiredExtensions'):
            for item in self.plan[key]:
                with self.subTest(key=key, item=item):
                    altered = copy.deepcopy(self.plan)
                    altered[key].remove(item)
                    with self.assertRaises(PlanError): validate(altered)

    def test_each_missing_target_and_wrong_java_is_rejected(self):
        for index in range(4):
            with self.subTest(index=index):
                altered = copy.deepcopy(self.plan)
                altered['cells'].pop(index)
                with self.assertRaises(PlanError): validate(altered)
                altered = copy.deepcopy(self.plan)
                altered['cells'][index]['javaMajor'] = 25
                with self.assertRaises(PlanError): validate(altered)

    def test_stale_candidate_and_atlas_format_are_rejected(self):
        self.plan['upstreamRevision'] = '6ee5bd56c1de8044c658d80d5a0c0f4a9d6c4085'
        with self.assertRaises(PlanError): validate(self.plan)
        self.setUp()
        self.plan['contracts']['atlasFormat'] = 10
        with self.assertRaises(PlanError): validate(self.plan)

    def test_lost_feature_or_unmapped_gate_is_rejected(self):
        for feature in tuple(self.plan['featureGates']):
            altered = copy.deepcopy(self.plan)
            altered['featureGates'].pop(feature)
            with self.assertRaises(PlanError): validate(altered)
        self.plan['featureGates']['F01'] = ['made-up-test']
        with self.assertRaises(PlanError): validate(self.plan)

    def test_assigning_an_unrelated_existing_fixture_cannot_hide_lost_behavior(self):
        self.plan['featureGates']['F01'] = ['creation-ui']
        with self.assertRaises(PlanError): validate(self.plan)

    def test_reduced_soak_or_removed_regression_guard_is_rejected(self):
        self.plan['performance']['soakMinutesMinimum'] = 1
        with self.assertRaises(PlanError): validate(self.plan)
        self.setUp()
        self.plan['contracts']['preserveLocalRecaptureFairness'] = False
        with self.assertRaises(PlanError): validate(self.plan)

    def test_false_readiness_or_cell_pass_is_rejected(self):
        self.plan['executionReady'] = True
        with self.assertRaises(PlanError): validate(self.plan)
        self.setUp()
        self.plan['cells'][0]['status'] = 'PASS'
        with self.assertRaises(PlanError): validate(self.plan)

    def test_unqualified_renderer_claim_is_rejected(self):
        self.plan['compatibility']['sodiumClaim'] = 'supported'
        with self.assertRaises(PlanError): validate(self.plan)

    def test_weakened_performance_limit_or_invented_platform_pass_is_rejected(self):
        self.plan['performance']['p99RegressionPercentMaximum'] = 99
        with self.assertRaises(PlanError): validate(self.plan)
        self.setUp()
        self.plan['platforms']['linuxDedicated'] = 'PASS'
        with self.assertRaises(PlanError): validate(self.plan)

    def test_malformed_contract_objects_fail_cleanly(self):
        for key in ('contracts', 'performance', 'compatibility'):
            altered = copy.deepcopy(self.plan)
            altered[key] = []
            with self.assertRaises(PlanError): validate(altered)

    def test_require_executable_cli_fails_closed(self):
        script = Path(__file__).with_name('validate_backport_acceptance_plan.py')
        result = subprocess.run([sys.executable, str(script), '--require-executable'],
                                capture_output=True, text=True, check=False)
        self.assertEqual(2, result.returncode)
        self.assertEqual('INVALID_OR_NOT_EXECUTABLE', json.loads(result.stdout)['status'])


if __name__ == '__main__':
    unittest.main()
