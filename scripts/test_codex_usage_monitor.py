#!/usr/bin/env python3

import unittest

import codex_usage_monitor as monitor


class CodexUsageMonitorTest(unittest.TestCase):
    def test_selects_weekly_window_instead_of_shorter_window(self):
        result = {"rateLimitsByLimitId": {"codex": {"limitId": "codex", "primary": {"usedPercent": 20, "windowDurationMins": 300, "resetsAt": 1}, "secondary": {"usedPercent": 46, "windowDurationMins": 10080, "resetsAt": 2}}}}
        selected = monitor.select_weekly_window(result)
        self.assertEqual(10080, selected["windowDurationMins"])
        self.assertEqual(46, selected["usedPercent"])
        self.assertEqual(2, selected["resetsAt"])

    def test_most_depleted_weekly_bucket_wins(self):
        result = {"rateLimitsByLimitId": {"one": {"limitId": "one", "primary": {"usedPercent": 40, "windowDurationMins": 10080}}, "two": {"limitId": "two", "primary": {"usedPercent": 70, "windowDurationMins": 10080}}}}
        self.assertEqual("two", monitor.select_weekly_window(result)["limitId"])

    def test_pause_threshold_is_inclusive(self):
        self.assertEqual("OK", monitor.classify_remaining(50.01, 50))
        self.assertEqual("PAUSE", monitor.classify_remaining(50, 50))
        self.assertEqual("PAUSE", monitor.classify_remaining(0, 50))

    def test_build_status_calculates_remaining_and_pause_state(self):
        window = {"limitId": "codex", "slot": "primary", "windowDurationMins": 10080, "usedPercent": 50, "resetsAt": 123}
        status = monitor.build_status(window, pause_threshold_percent=50)
        self.assertEqual(50, status["remainingPercent"])
        self.assertEqual("PAUSE", status["state"])
        self.assertIn("PAUSE ALL RINGWORLD WORK", monitor.format_status(status))

    def test_missing_weekly_window_is_not_mislabelled(self):
        result = {"rateLimits": {"limitId": "codex", "primary": {"usedPercent": 20, "windowDurationMins": 300}}}
        with self.assertRaises(monitor.MonitorError):
            monitor.select_weekly_window(result)

    def test_parse_args_defaults_to_fifty_percent_pause_threshold(self):
        self.assertEqual(50, monitor.parse_args([]).pause_threshold)


if __name__ == "__main__":
    unittest.main()
