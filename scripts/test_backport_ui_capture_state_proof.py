import unittest
from scripts.backport_ui_capture_state_proof import EXPECTED, validate


def lines():
    return [f'[atlas-ui-test] rendered-state capture={name} state={state} acknowledgedFrame={100+i*10} captureFrame={103+i*10}'
            for i, (name, state) in enumerate(EXPECTED.items())]


class RenderedStateProof(unittest.TestCase):
    def test_actual_ordered_boundaries_are_required(self):
        self.assertEqual(validate('\n'.join(lines()))['status'], 'PASS')

    def test_old_stale_caption_run_cannot_pass_without_boundaries(self):
        with self.assertRaises(ValueError): validate('[atlas-ui-test] PASS: pause/resume/cancel')

    def test_request_time_cannot_replace_rendered_acknowledgement(self):
        values = lines(); values[2] = values[2].replace('captureFrame=123', 'captureFrame=120')
        with self.assertRaises(ValueError): validate('\n'.join(values))

    def test_resumed_capture_cannot_report_paused_state(self):
        values = lines(); values[2] = values[2].replace('state=RUNNING', 'state=PAUSED')
        with self.assertRaises(ValueError): validate('\n'.join(values))

    def test_missing_duplicate_and_reordered_boundaries_fail(self):
        for values in [lines()[:-1], lines()+lines()[:1], list(reversed(lines()))]:
            with self.assertRaises(ValueError): validate('\n'.join(values))

    def test_overlapping_acknowledgement_windows_fail(self):
        values = lines(); values[1] = values[1].replace('acknowledgedFrame=110', 'acknowledgedFrame=102')
        with self.assertRaises(ValueError): validate('\n'.join(values))


if __name__ == '__main__': unittest.main()
