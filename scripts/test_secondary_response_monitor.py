#!/usr/bin/env python3

import unittest

import secondary_response_monitor as monitor


def response(comment_id, issue=9):
    return {
        "id": comment_id,
        "issueNumber": issue,
        "createdAt": "2026-07-28T10:00:00Z",
        "updatedAt": "2026-07-28T10:00:00Z",
        "url": "https://github.com/Delaser/RingWorld/issues/{0}#x".format(issue),
        "summary": "[SECONDARY][ACK S2]",
    }


class SecondaryResponseMonitorTest(unittest.TestCase):
    def test_first_poll_baselines_without_pending_old_responses(self):
        status, notify = monitor.build_polled_status(
            [response(10), response(20)], None, "Delaser/RingWorld"
        )
        self.assertEqual("WAITING", status["state"])
        self.assertEqual(20, status["lastAcknowledgedCommentId"])
        self.assertFalse(notify)

    def test_recovery_from_cursorless_error_rebaselines(self):
        status, notify = monitor.build_polled_status(
            [response(10), response(20)],
            {"state": "ERROR", "error": "temporary"},
            "Delaser/RingWorld",
        )
        self.assertEqual("WAITING", status["state"])
        self.assertEqual(20, status["lastAcknowledgedCommentId"])
        self.assertFalse(notify)

    def test_new_response_remains_pending_until_acknowledged(self):
        previous = {
            "lastAcknowledgedCommentId": 20,
            "lastNotifiedCommentId": 20,
        }
        status, notify = monitor.build_polled_status(
            [response(20), response(30)], previous, "Delaser/RingWorld"
        )
        self.assertEqual("RESPONSE_PENDING", status["state"])
        self.assertEqual([30], [item["id"] for item in status["pendingResponses"]])
        self.assertTrue(notify)

        repeated, repeated_notify = monitor.build_polled_status(
            [response(20), response(30)], status, "Delaser/RingWorld"
        )
        self.assertEqual("RESPONSE_PENDING", repeated["state"])
        self.assertFalse(repeated_notify)

    def test_acknowledge_clears_pending_responses(self):
        status = {
            "state": "RESPONSE_PENDING",
            "lastPolledCommentId": 30,
            "lastAcknowledgedCommentId": 20,
            "lastNotifiedCommentId": 30,
            "pendingResponses": [response(30)],
        }
        acknowledged = monitor.acknowledge(status)
        self.assertEqual("WAITING", acknowledged["state"])
        self.assertEqual(30, acknowledged["lastAcknowledgedCommentId"])
        self.assertEqual([], acknowledged["pendingResponses"])

    def test_only_protocol_prefixed_comments_are_selected(self):
        comments = [
            {
                "id": 1,
                "body": "[PRIMARY][ASSIGN S2]",
                "issue_url": "https://api.github.com/repos/a/b/issues/9",
            },
            {
                "id": 2,
                "body": "  [SECONDARY][HANDOFF S2]\nDetails",
                "issue_url": "https://api.github.com/repos/a/b/issues/9",
                "html_url": "https://example.test/2",
            },
        ]
        selected = monitor.secondary_comments(comments)
        self.assertEqual([2], [item["id"] for item in selected])
        self.assertEqual(9, selected[0]["issueNumber"])


if __name__ == "__main__":
    unittest.main()
