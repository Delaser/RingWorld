#!/usr/bin/env python3

import unittest

import codex_usage_monitor as monitor


class CodexUsageMonitorTest(unittest.TestCase):
    def test_selects_weekly_window_instead_of_shorter_window(self):
        result = {
            "rateLimitsByLimitId": {
                "codex": {
                    "limitId": "codex",
                    "primary": {
                        "usedPercent": 20,
                        "windowDurationMins": 300,
                        "resetsAt": 1,
                    },
                    "secondary": {
                        "usedPercent": 46,
                        "windowDurationMins": 10080,
                        "resetsAt": 2,
                    },
                }
            }
        }
        selected = monitor.select_weekly_window(result)
        self.assertEqual(10080, selected["windowDurationMins"])
        self.assertEqual(46, selected["usedPercent"])

    def test_most_depleted_weekly_bucket_wins(self):
        result = {
            "rateLimitsByLimitId": {
                "one": {
                    "limitId": "one",
                    "primary": {
                        "usedPercent": 40,
                        "windowDurationMins": 10080,
                    },
                },
                "two": {
                    "limitId": "two",
                    "primary": {
                        "usedPercent": 70,
                        "windowDurationMins": 10080,
                    },
                },
            }
        }
        self.assertEqual("two", monitor.select_weekly_window(result)["limitId"])

    def test_threshold_states_protect_ten_percent_reserve(self):
        self.assertEqual("OK", monitor.classify_remaining(16, 10, 5))
        self.assertEqual("HOLD", monitor.classify_remaining(15, 10, 5))
        self.assertEqual("HOLD", monitor.classify_remaining(11, 10, 5))
        self.assertEqual("BLOCK", monitor.classify_remaining(10, 10, 5))

    def test_missing_weekly_window_is_not_mislabelled(self):
        result = {
            "rateLimits": {
                "limitId": "codex",
                "primary": {
                    "usedPercent": 20,
                    "windowDurationMins": 300,
                },
            }
        }
        with self.assertRaises(monitor.MonitorError):
            monitor.select_weekly_window(result)

    def test_build_status_calculates_remaining(self):
        status = monitor.build_status(
            {
                "limitId": "codex",
                "slot": "primary",
                "windowDurationMins": 10080,
                "usedPercent": 46,
                "resetsAt": 123,
            },
            reserve_percent=10,
            safety_margin_percent=5,
        )
        self.assertEqual(54, status["remainingPercent"])
        self.assertEqual("OK", status["state"])


if __name__ == "__main__":
    unittest.main()
