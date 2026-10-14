#!/usr/bin/env python3
"""Check one consumer-compatibility run (see compat/run.sh).

Usage: check_report.py OUTPUT_DIR JUNIT_VERSION KIND

OUTPUT_DIR holds the consumer's ctrf-report.json and junit-versions.txt (written by its passing test).
KIND is "jupiter" (Gradle, JUnit Jupiter), "vintage" (Gradle, JUnit 4 tests only, no Jupiter on the classpath)
or "maven" (Maven, JUnit Jupiter, two test classes in separate Surefire forks, failed tests rerun once).
The run passes when the consumer's tests are in the CTRF report as expected, the report has a runId, and the
consumer ran on the JUnit version it asked for.
"""
import json
import sys
from pathlib import Path

# tests, passed, failed, flaky
EXPECTED_SUMMARY = {
    "jupiter": (2, 1, 1, 0),
    "vintage": (2, 1, 1, 0),
    # CompatTest.passes; CompatTest.fails, rerun and failing again; RerunTest.flakyOnce, passing on its rerun
    "maven": (3, 2, 1, 1),
}


def platform_version(junit_version):
    # JUnit 5.x.y ships platform 1.x.y; from JUnit 6 on, all artifacts share one version.
    major, rest = junit_version.split(".", 1)
    return "1." + rest if major == "5" else junit_version


def report_problems(out_dir, kind):
    report = out_dir / "ctrf-report.json"
    if not report.exists():
        return [f"no CTRF report at {report}"]
    document = json.loads(report.read_text())
    summary = document["results"]["summary"]
    problems = []
    counts = (summary["tests"], summary["passed"], summary["failed"], summary.get("flaky", 0))
    if counts != EXPECTED_SUMMARY[kind]:
        problems.append(f"CTRF summary tests/passed/failed/flaky is {counts}, expected {EXPECTED_SUMMARY[kind]}")
    if not document.get("runId"):
        problems.append("the CTRF report has no runId")
    if kind == "maven":
        flaky = [test for test in document["results"]["tests"] if test["name"] == "flakyOnce()"]
        if len(flaky) != 1 or flaky[0].get("retries") != 1 or flaky[0].get("flaky") is not True:
            problems.append(f"flakyOnce() should be one entry with retries 1 and flaky true, got {flaky}")
        forks = sorted(out_dir.glob("fork-*.pid"))
        pids = {fork.read_text().strip() for fork in forks}
        if len(forks) != 2 or len(pids) != 2:
            problems.append(f"expected the two test classes in two JVMs, got {[f.name for f in forks]} with pids {sorted(pids)}")
    return problems


def version_problems(out_dir, junit_version, kind):
    versions_file = out_dir / "junit-versions.txt"
    if not versions_file.exists():
        return [f"no {versions_file}: the consumer's passing test did not run"]
    versions = dict(line.split("=", 1) for line in versions_file.read_text().split())
    expected = {"platform-launcher": platform_version(junit_version)}
    if kind == "vintage":
        expected["jupiter-api"] = "absent"
        expected["vintage-engine"] = junit_version
    else:
        expected["jupiter-api"] = junit_version
    return [f"{artifact} on the test classpath is {versions.get(artifact)}, expected {want}"
            for artifact, want in expected.items() if versions.get(artifact) != want]


def main():
    if len(sys.argv) != 4 or sys.argv[3] not in EXPECTED_SUMMARY:
        sys.exit(__doc__)
    out_dir, junit_version, kind = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
    problems = report_problems(out_dir, kind) + version_problems(out_dir, junit_version, kind)
    for problem in problems:
        print("  - " + problem)
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
