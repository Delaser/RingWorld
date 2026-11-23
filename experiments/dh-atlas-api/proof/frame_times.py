"""Summarize render intervals around the first detailed draw in a Large probe CSV."""
import csv
from math import ceil
from pathlib import Path
import sys


def report(name, rows):
    intervals = sorted(duration / 1_000_000 for _, duration, _ in rows)
    if not intervals:
        return
    def percentile(percent):
        return intervals[ceil(len(intervals) * percent / 100) - 1]
    print(f"{name:24} {len(rows):8} {percentile(50):8.2f} {percentile(95):8.2f}"
          f" {percentile(99):8.2f} {intervals[-1]:8.2f} {sum(t > 50 for t in intervals):6}")


def main(path):
    with path.open(newline="") as stream:
        rows = [tuple(map(int, row)) for row in csv.reader(stream)]
    if not rows:
        raise SystemExit("No measured frames")
    start = rows[0][0]
    swap_index = next((i for i, row in enumerate(rows) if row[2]), None)
    swap = None if swap_index is None else rows[swap_index][0]
    second = 1_000_000_000
    print("Phase                      Frames   P50 ms   P95 ms   P99 ms   Max ms  >50ms")
    report("Opening, 5-45s", [r for r in rows if 5 * second <= r[0] - start < 45 * second])
    report("Generating", [r for r in rows if not r[2] and r[0] - start >= 5 * second
                           and (swap is None or r[0] < swap - second)])
    if swap is None:
        print("No detailed draw recorded")
        return
    report("Handoff (+/-1s)", [r for r in rows if abs(r[0] - swap) <= second])
    report("Detailed, +2s onward", [r for r in rows if r[0] >= swap + 2 * second])
    print("Seconds to first detailed draw:", round((swap - start) / second, 2))
    print("Intervals ending at previous, first detailed and following draw (ms):",
          [round(r[1] / 1_000_000, 2) for r in rows[max(0, swap_index-1):swap_index+2]])


if __name__ == "__main__":
    main(Path(sys.argv[1]))
