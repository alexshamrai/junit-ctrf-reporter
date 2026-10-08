#!/usr/bin/env python3
"""Check one consumer-compatibility run (see compat/run.sh).

Usage: check_report.py OUTPUT_DIR JUNIT_VERSION KIND

OUTPUT_DIR holds the consumer's ctrf-report.json and junit-versions.txt (written by its passing test).
KIND is "jupiter" (JUnit Jupiter consumer) or "vintage" (JUnit 4 tests only, no Jupiter on the classpath).
The run passes when the consumer's two tests (one passing, one failing) are in the CTRF report and the
consumer ran on the JUnit version it asked for.
"""
import json
import sys
from pathlib import Path


def platform_version(junit_version):
    # JUnit 5.x.y ships platform 1.x.y; from JUnit 6 on, all artifacts share one version.
    major, rest = junit_version.split(".", 1)
    return "1." + rest if major == "5" else junit_version


def problems_in(out_dir, junit_version, kind):
    problems = []
    report = out_dir / "ctrf-report.json"
    if report.exists():
        summary = json.loads(report.read_text())["results"]["summary"]
        counts = (summary["tests"], summary["passed"], summary["failed"])
        if counts != (2, 1, 1):
            problems.append(f"CTRF summary tests/passed/failed is {counts}, expected (2, 1, 1)")
    else:
        problems.append(f"no CTRF report at {report}")

    versions_file = out_dir / "junit-versions.txt"
    if not versions_file.exists():
        problems.append(f"no {versions_file}: the consumer's passing test did not run")
        return problems
    versions = dict(line.split("=", 1) for line in versions_file.read_text().split())
    expected = {"platform-launcher": platform_version(junit_version)}
    if kind == "jupiter":
        expected["jupiter-api"] = junit_version
    else:
        expected["jupiter-api"] = "absent"
        expected["vintage-engine"] = junit_version
    for artifact, want in expected.items():
        if versions.get(artifact) != want:
            problems.append(f"{artifact} on the test classpath is {versions.get(artifact)}, expected {want}")
    return problems


def main():
    if len(sys.argv) != 4 or sys.argv[3] not in ("jupiter", "vintage"):
        sys.exit(__doc__)
    problems = problems_in(Path(sys.argv[1]), sys.argv[2], sys.argv[3])
    for problem in problems:
        print("  - " + problem)
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
