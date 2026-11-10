#!/usr/bin/env python3
"""Fail closed on a GameTest server run that silently did nothing.

Gradle reports BUILD SUCCESSFUL even when the GameTest server fails to start
or ran zero tests, so a green `./gradlew runGameTestServer` exit code alone
proves nothing. This checks the run's own log for unambiguous evidence: the
server must not have failed to start, there must be no reported
required-test failures, and the completed/passed counts must both exactly
equal the number of acceptance tests this mod declares (MINECRAFT-500).

Live NeoForge GameTestServer log format (confirmed by an actual CI run, not
assumed): passing tests are only ever counted, never individually named --
"Running test batch 'defaultBatch:0' (3 tests)..." -- so a per-test-name
substring check (as Courier Craft's MINECRAFT-493 guard does, whose overall
pattern this is adapted from) cannot work here; only a failing test gets an
explicit "<lowercased name> failed at ..." line. Matching the exact expected
count is the strongest check this log format actually supports.
"""
import re
import sys
from pathlib import Path


class RuntimeEvidenceError(ValueError):
    """The GameTest log cannot prove a complete, non-trivial run."""


EXPECTED_REQUIRED_TEST_COUNT = 2  # smoothMaterialsResolves.../smoothSurfaceFeatureIsRegistered...


def verify_runtime_log(log_text):
    if re.search(r"Failed to start the minecraft server", log_text, re.I):
        raise RuntimeEvidenceError("GameTest server startup failed")
    if re.search(r"\b\d+\s+required tests failed\b", log_text):
        raise RuntimeEvidenceError("required GameTest failure evidence")
    completed = list(re.finditer(r"=+\s*(\d+)\s+GAME TESTS COMPLETE\s+IN\s+", log_text))
    passed = list(re.finditer(r"All\s+(\d+)\s+required tests passed", log_text))
    if len(completed) != 1 or len(passed) != 1:
        raise RuntimeEvidenceError("missing or ambiguous completion/pass summary")
    if completed[0].end() > passed[0].start() or completed[0].group(1) != passed[0].group(1):
        raise RuntimeEvidenceError("completion/pass summaries are not one consistent terminal pair")
    count = int(completed[0].group(1))
    if count != EXPECTED_REQUIRED_TEST_COUNT:
        raise RuntimeEvidenceError(
            f"expected exactly {EXPECTED_REQUIRED_TEST_COUNT} required GameTests to complete and pass, got {count}")
    return count


def main(argv):
    if len(argv) != 2:
        raise SystemExit(f"usage: {argv[0]} <gametest.log>")
    try:
        passed = verify_runtime_log(Path(argv[1]).read_text())
    except (OSError, RuntimeEvidenceError) as error:
        raise SystemExit(f"GameTest count guard failed: {error}") from error
    print(f"GameTest count guard passed: {passed} required coinstalled-runtime cases")


if __name__ == "__main__":
    main(sys.argv)
