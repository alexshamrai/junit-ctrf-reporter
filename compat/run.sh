#!/usr/bin/env bash
# Consumer-compatibility check.
#
# Publishes the library to build/compat-repo as 0.0.0-compat-SNAPSHOT, then builds small consumer
# projects against each given JUnit version:
#   maven/pom.xml             Maven + CtrfListener, JUnit declared before the reporter
#   maven/pom-ctrf-first.xml  Maven + CtrfListener, reporter declared before JUnit
#   gradle-jupiter            Gradle + CtrfExtension
#   gradle-vintage            Gradle + CtrfListener, JUnit 4 tests only (no Jupiter on the classpath)
# Each consumer runs one passing and one failing test; check_report.py then verifies the CTRF report
# and that the consumer ran on the JUnit version it asked for.
#
# Usage: compat/run.sh JUNIT_VERSION...    e.g. compat/run.sh 5.10.5 5.14.4 6.1.3
# Needs: JDK 17+, Maven, Python 3.
set -euo pipefail

if [ $# -eq 0 ]; then
  echo "usage: $0 JUNIT_VERSION..." >&2
  exit 2
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPAT="$ROOT/compat"
CTRF_VERSION="0.0.0-compat-SNAPSHOT"
CTRF_REPO="$ROOT/build/compat-repo"
LOGS="$ROOT/build/compat-logs"

rm -rf "$CTRF_REPO" "$LOGS"
mkdir -p "$LOGS"
if ! "$ROOT/gradlew" -p "$ROOT" publishMavenPublicationToCompatRepository -PpublishVersion="$CTRF_VERSION" \
    > "$LOGS/publish.log" 2>&1; then
  tail -n 20 "$LOGS/publish.log"
  echo "publishing the library to $CTRF_REPO failed"
  exit 1
fi

failures=0

# run_consumer LABEL OUTPUT_DIR JUNIT_VERSION KIND COMMAND...
run_consumer() {
  local label=$1 out_dir=$2 junit=$3 kind=$4
  shift 4
  local log="$LOGS/${label//\//_}-$junit.log"
  if ! "$@" > "$log" 2>&1; then
    echo "  build failed (full log: $log):"
    grep -E '\[ERROR\] [A-Za-z]|What went wrong|Error:|Exception' "$log" | head -n 4 | sed 's/^/    /' || true
  fi
  if python3 "$COMPAT/check_report.py" "$out_dir" "$junit" "$kind"; then
    echo "PASS $label (JUnit $junit)"
  else
    echo "FAIL $label (JUnit $junit)"
    failures=$((failures + 1))
  fi
}

for junit in "$@"; do
  for pom in pom.xml pom-ctrf-first.xml; do
    run_consumer "maven/$pom" "$COMPAT/maven/target" "$junit" jupiter \
      mvn -B -U -f "$COMPAT/maven/$pom" clean test -Dmaven.test.failure.ignore=true \
        -Djunit.version="$junit" -Dctrf.version="$CTRF_VERSION" -Dctrf.repo="file://$CTRF_REPO"
  done
  for project in gradle-jupiter gradle-vintage; do
    kind=jupiter
    if [ "$project" = gradle-vintage ]; then kind=vintage; fi
    run_consumer "$project" "$COMPAT/$project/build" "$junit" "$kind" \
      "$ROOT/gradlew" -p "$COMPAT/$project" clean test \
        -PjunitVersion="$junit" -PctrfVersion="$CTRF_VERSION" -PctrfRepo="$CTRF_REPO"
  done
done

if [ "$failures" -ne 0 ]; then
  echo "$failures compatibility check(s) failed"
  exit 1
fi
echo "all compatibility checks passed"
