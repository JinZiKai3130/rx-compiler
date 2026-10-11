#!/usr/bin/env python3
"""Frontend acceptance over tests/official: lexer + parser stages.

Runs ./scripts/rxc per manifest entry:

  lex   : `rxc --stage lex <file>`          exit 0 <=> lexes cleanly
          (invalid tokens INVALID_* / ERROR_CHAR / UNTERMINATED_BLOCK_COMMENT count as errors)
  parse : `rxc --entry <entry> --dump-ast <file>`
            exit 0 -> parsed AND the AST was built
            exit 3 -> parsed (good), but the AST builder does not support the construct yet
            exit 1 -> syntax errors (expected when compilation_success is false)

Build first:  make compiler
Usage:        scripts/check_frontend.py [lex|parse] [SUBSTR ...] [-v]
              SUBSTR restricts cases by source-path substring (e.g. 0001); stages default to both;
              -v prints each matched case's rxc output (AST dump / first error lines).
Exit status:  0 = everything matched, 1 = mismatches / AST gaps, 2 = setup problem.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RXC = ROOT / "scripts" / "rxc"
STAGE_DIRS = {
    "lex": ROOT / "tests" / "official" / "lexer",
    "parse": ROOT / "tests" / "official" / "parser",
}
MAX_LISTED = 60


def run_rxc(args: list[str]) -> subprocess.CompletedProcess:
    return subprocess.run([str(RXC), *args], capture_output=True, text=True, timeout=60)


def load_manifest(case_dir: Path) -> list[dict]:
    with open(case_dir / "manifest.json", encoding="utf-8") as f:
        return json.load(f)


def one_line(text: str) -> str:
    text = text.strip()
    return text.splitlines()[0] if text else "(no message)"


def print_failures(title: str, items: list[str]) -> None:
    if not items:
        return
    print(f"  {title} ({len(items)}):")
    for item in items[:MAX_LISTED]:
        print(f"    {item}")
    if len(items) > MAX_LISTED:
        print(f"    ... {len(items) - MAX_LISTED} more")


def select(cases: list[dict], filters: list[str]) -> list[dict]:
    """Keep only cases whose source path contains one of the substrings."""
    if not filters:
        return cases
    return [c for c in cases if any(f in c["source"] for f in filters)]


def print_matched(label: str, ok: bool, stdout: str, stderr: str) -> None:
    print(f"  [{'PASS' if ok else 'FAIL'}] {label}")
    if stdout.strip():
        for line in stdout.strip().splitlines():
            print(f"    {line}")
    lines = [line for line in stderr.strip().splitlines() if line]
    for line in lines[:5]:
        print(f"    {line}")
    if len(lines) > 5:
        print(f"    ... {len(lines) - 5} more lines")


def check_lex(case_dir: Path, filters: list[str], verbose: bool):
    cases = select([c for c in load_manifest(case_dir) if c["stage"] == "lex"], filters)
    passed = 0
    failures: list[str] = []
    for i, case in enumerate(cases, 1):
        src = case_dir / case["source"]
        proc = run_rxc(["--stage", "lex", str(src)])
        expected = bool(case["compilation_success"])
        ok = (proc.returncode == 0) == expected
        if ok:
            passed += 1
        else:
            want = "accept" if expected else "reject"
            failures.append(f"{case['source']}: expected {want}, rc={proc.returncode}: {one_line(proc.stderr)}")
        if verbose:
            print_matched(case["source"], ok, proc.stdout, proc.stderr)
        if not filters and i % 100 == 0:
            print(f"  ... {i}/{len(cases)}")
    return len(cases), passed, failures


def check_parse(case_dir: Path, filters: list[str], verbose: bool):
    cases = select([c for c in load_manifest(case_dir) if c["stage"] == "parse"], filters)
    accepts = parse_passed = ast_built = 0
    parse_failures: list[str] = []
    ast_failures: list[str] = []
    for i, case in enumerate(cases, 1):
        src = case_dir / case["source"]
        entry = case.get("metadata", {}).get("entry", "crate")
        proc = run_rxc(["--entry", entry, "--dump-ast", str(src)])
        rc = proc.returncode
        expected = bool(case["compilation_success"])
        ok = False
        if expected:
            accepts += 1
            if rc == 0:
                parse_passed += 1
                ast_built += 1
                ok = True
            elif rc == 3:
                parse_passed += 1
                ast_failures.append(f"{case['source']} (entry={entry}): {one_line(proc.stderr)}")
            else:
                parse_failures.append(
                    f"{case['source']} (entry={entry}): expected accept, rc={rc}: {one_line(proc.stderr)}"
                )
        else:
            if rc == 1:
                parse_passed += 1
                ok = True
            else:
                parse_failures.append(
                    f"{case['source']} (entry={entry}): expected reject, rc={rc}: {one_line(proc.stderr)}"
                )
        if verbose:
            print_matched(f"{case['source']} (entry={entry})", ok, proc.stdout, proc.stderr)
        if not filters and i % 100 == 0:
            print(f"  ... {i}/{len(cases)}")
    return len(cases), accepts, parse_passed, ast_built, parse_failures, ast_failures


def main() -> int:
    if not (ROOT / "build" / "kotlin-classes").is_dir() or not os.access(RXC, os.X_OK):
        print("build missing — run `make compiler` first.")
        return 2

    stages: list[str] = []
    filters: list[str] = []
    verbose = False
    for arg in sys.argv[1:]:
        if arg in ("-v", "--verbose"):
            verbose = True
        elif arg.startswith("-"):
            print(f"unknown option: {arg}")
            print("usage: check_frontend.py [lex|parse] [SUBSTR ...] [-v]")
            return 2
        elif arg in STAGE_DIRS:
            stages.append(arg)
        else:
            filters.append(arg)  # case path substring, e.g. `0001`
    if not stages:
        stages = ["lex", "parse"]

    ok = True
    matched_total = 0
    for stage in stages:
        case_dir = STAGE_DIRS[stage]
        print(f"== stage {stage}: {case_dir.relative_to(ROOT)} ==")
        start = time.time()

        if stage == "lex":
            total, passed, failures = check_lex(case_dir, filters, verbose)
            matched_total += total
            print(f"  lex: {passed}/{total} passed")
            if failures:
                ok = False
                print_failures("mismatches", failures)
        else:
            total, accepts, parse_passed, ast_built, parse_failures, ast_failures = check_parse(
                case_dir, filters, verbose
            )
            matched_total += total
            print(f"  parse: {parse_passed}/{total} passed ({accepts} accept cases)")
            print(f"  ast:   built {ast_built}/{accepts} accepted cases")
            if parse_failures:
                ok = False
                print_failures("parse mismatches", parse_failures)
            if ast_failures:
                ok = False
                print_failures("AST build failures on accepted cases", ast_failures)

        print(f"  ({time.time() - start:.1f}s)")
        print()

    if filters and matched_total == 0:
        print(f"no cases matched: {' '.join(filters)}")
        return 2

    print("SUMMARY:", "OK" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
