from __future__ import annotations

import re
from pathlib import Path

from _python_guardrails import (
    assert_contains,
    assert_not_contains,
    ensure_path_exists,
    fail,
    read_text,
    repo_root,
    write_step,
)


def get_referenced_test_files(content: str) -> list[str]:
    pattern = re.compile(r"`(?P<file>(?:[^`]+Test\.java|[^`]+\.test\.(?:ts|tsx)))`")
    files = []
    for match in pattern.finditer(content):
        candidate = match.group("file").strip()
        if "*" in candidate:
            continue
        files.append(Path(candidate).name)
    return sorted(set(files))


def get_test_file_basenames(root: Path, patterns: list[str]) -> list[str]:
    if not root.exists():
        return []
    names: set[str] = set()
    for pattern in patterns:
        for path in root.rglob(pattern):
            if path.is_file():
                names.add(path.name)
    return sorted(names)


def get_audit_gap_summary_counts(lines: list[str]) -> dict[str, int]:
    counts = {"critical": 0, "medium": 0, "low": 0}
    pattern = re.compile(r"^\|\s+[^|]+\|\s+AC\d+\s+\|.*\|\s+gap\s+\|\s+(critical|medium|low)\s+\|\s*$")
    for line in lines:
        match = pattern.match(line)
        if match:
            counts[match.group(1)] += 1
    return counts


def get_audit_summary_value(lines: list[str], label: str) -> int:
    for line in lines:
        columns = [column.strip() for column in line.split("|") if column.strip()]
        if len(columns) >= 2 and columns[0] == label:
            return int(columns[1].replace("*", ""))
    fail(f"Missing audit summary row for '{label}'.")


def get_fr_status(lines: list[str], fr_id: int) -> str:
    pattern = re.compile(rf"^\| FR{fr_id} \|")
    for line in lines:
        if pattern.search(line):
            columns = [column.strip() for column in line.split("|") if column.strip()]
            if len(columns) < 7:
                fail(f"FR{fr_id} row does not have enough columns: {line}")
            return columns[4]
    fail(f"Missing FR{fr_id} row in requirements traceability matrix.")


def main() -> None:
    root = repo_root()
    traceability_path = root / "docs/requirements-traceability.md"
    audit_path = root / "_bmad-output/implementation-artifacts/10-1-ac-coverage-gap-audit.md"
    pr_template_path = root / ".github/PULL_REQUEST_TEMPLATE.md"

    write_step("Checking traceability artifacts exist")
    ensure_path_exists(traceability_path, "Requirements traceability document")
    ensure_path_exists(audit_path, "AC coverage audit")
    ensure_path_exists(pr_template_path, "PR template")

    traceability = read_text(traceability_path)
    audit = read_text(audit_path)
    pr_template = read_text(pr_template_path)
    audit_lines = audit.splitlines()

    write_step("Checking canonical coverage vocabulary and hook references")
    assert_contains(traceability, "## Coverage Status Vocabulary", "Traceability document is missing the coverage vocabulary section.")
    for needle in ["AUTO", "MANUAL", "PARTIAL", "GAP"]:
        assert_contains(traceability, needle, f"Traceability coverage vocabulary is missing '{needle}'.")
    assert_contains(traceability, "_bmad-output/implementation-artifacts/10-1-ac-coverage-gap-audit.md", "Traceability AC-level source reference is missing.")
    assert_contains(traceability, ".github/PULL_REQUEST_TEMPLATE.md", "Traceability process hook reference is missing.")
    assert_contains(traceability, "scripts/validate-traceability-matrix.py", "Traceability validation reference is missing.")

    write_step("Checking FR rows and status discipline")
    fr_lines = [line for line in traceability.splitlines() if re.match(r"^\| FR\d{1,2} \|", line)]
    if len(fr_lines) != 32:
        fail(f"Expected 32 FR rows with canonical statuses, found {len(fr_lines)}.")

    fr_ids: list[int] = []
    for line in fr_lines:
        match = re.match(r"^\| FR(\d{1,2}) \|", line)
        if match is None:
            fail(f"FR row has unexpected format: {line}")
        columns = [column.strip() for column in line.split("|") if column.strip()]
        if len(columns) < 7:
            fail(f"FR row does not have enough columns: {line}")
        status = columns[4]
        if status not in {"AUTO", "MANUAL", "PARTIAL", "GAP"}:
            fail(f"FR row has invalid status '{status}': {line}")
        fr_ids.append(int(match.group(1)))

    for fr_id in range(1, 33):
        if fr_id not in fr_ids:
            fail(f"Missing FR{fr_id} row in requirements traceability matrix.")

    if re.search(r"\bTODO\b", traceability):
        fail("Traceability document still contains TODO placeholders.")

    for fr_id in [12, 31, 32]:
        if get_fr_status(fr_lines, fr_id) not in {"AUTO", "MANUAL", "PARTIAL"}:
            fail(f"FR{fr_id} must not remain a GAP after traceability normalization.")

    write_step("Checking legacy frontend path drift is removed from traceability surfaces")
    assert_not_contains(traceability, "frontend/src/", "Traceability document contains legacy frontend/src/ path drift.")
    assert_not_contains(audit, "frontend/src/", "AC coverage audit contains legacy frontend/src/ path drift.")

    write_step("Checking referenced audit test files exist in the repository")
    backend_test_files = get_test_file_basenames(root / "backend/src/test", ["*Test.java"])
    frontend_test_files = get_test_file_basenames(root / "frontend-qwik/src", ["*.test.ts", "*.test.tsx"])
    referenced_test_files = get_referenced_test_files(audit)
    missing_referenced_files: list[str] = []
    for referenced_file in referenced_test_files:
        if referenced_file.endswith("Test.java"):
            if referenced_file not in backend_test_files:
                missing_referenced_files.append(referenced_file)
            continue
        if referenced_file.endswith((".test.ts", ".test.tsx")) and referenced_file not in frontend_test_files:
            missing_referenced_files.append(referenced_file)

    if missing_referenced_files:
        fail("AC coverage audit references test files that do not exist: " + ", ".join(sorted(set(missing_referenced_files))))

    write_step("Checking audit summary counts match gap rows")
    gap_counts = get_audit_gap_summary_counts(audit_lines)
    critical_summary = get_audit_summary_value(audit_lines, "critical")
    medium_summary = get_audit_summary_value(audit_lines, "medium")
    low_summary = get_audit_summary_value(audit_lines, "low")
    total_summary = get_audit_summary_value(audit_lines, "**ИТОГО**")
    computed_total = gap_counts["critical"] + gap_counts["medium"] + gap_counts["low"]

    if critical_summary != gap_counts["critical"]:
        fail(f"AC coverage audit critical gap count mismatch: summary={critical_summary}, computed={gap_counts['critical']}")
    if medium_summary != gap_counts["medium"]:
        fail(f"AC coverage audit medium gap count mismatch: summary={medium_summary}, computed={gap_counts['medium']}")
    if low_summary != gap_counts["low"]:
        fail(f"AC coverage audit low gap count mismatch: summary={low_summary}, computed={gap_counts['low']}")
    if total_summary != computed_total:
        fail(f"AC coverage audit total gap count mismatch: summary={total_summary}, computed={computed_total}")

    write_step("Checking PR template includes traceability checklist")
    assert_contains(pr_template, "traceability", "PR template is missing traceability checklist wording.")
    assert_contains(pr_template, "requirements-traceability.md", "PR template is missing requirements traceability reference.")
    assert_contains(pr_template, "48 hours", "PR template is missing the 48 hours SLA wording.")

    write_step("Traceability artifacts validated successfully")


if __name__ == "__main__":
    main()
