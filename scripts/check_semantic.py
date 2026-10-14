#!/usr/bin/env python3
"""Semantic acceptance over tests/official/semantic.

Runs ./scripts/rxc --stage semantic <file> for every manifest entry with
stage == "semantic" (docs/semantic_design.md):

  accept case: exit 0                                -> pass
  reject case: exit 1 (normal diagnostic rejection)  -> pass

Anything else (crash, timeout, exit 3, ...) is a mismatch — the official
runner (scripts/test.py) requires normal rejection, not a crash or timeout.

While the semantic checks are still being implemented (WP0–WP5), every reject
case shows up as backlog; the per-directory table is the progress tracker and
the script exits 1 until the backlog is empty (WP6).

Build first:  make compiler
Usage:        scripts/check_semantic.py [SUBSTR ...] [-v]
              SUBSTR filters cases by source-path substring (e.g. arrays, entry);
              -v prints one line per executed case plus its first error lines.
Exit status:  0 = everything matched, 1 = mismatches, 2 = setup problem.
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
CASE_DIR = ROOT / "tests" / "official" / "semantic"
MAX_LISTED = 60


def run_rxc(args: list[str]) -> subprocess.CompletedProcess | None:
    try:
        return subprocess.run([str(RXC), *args], capture_output=True, text=True, timeout=60)
    except subprocess.TimeoutExpired:
        return None


def one_line(text: str) -> str:
    text = text.strip()
    return text.splitlines()[0] if text else "(no message)"


def load_cases() -> list[dict]:
    cases = []
    for manifest in sorted(CASE_DIR.rglob("manifest.json")):
        directory = manifest.parent.relative_to(CASE_DIR).parts
        for entry in json.loads(manifest.read_text(encoding="utf-8")):
            if entry.get("stage") != "semantic":
                continue
            label = "/".join((*directory, entry["source"]))
            cases.append({
                "dir": directory[0] if directory else ".",
                "source": manifest.parent / entry["source"],
                "label": label,
                "success": bool(entry["compilation_success"]),
            })
    return sorted(cases, key=lambda c: c["label"])


def select(cases: list[dict], filters: list[str]) -> list[dict]:
    if not filters:
        return cases
    return [c for c in cases if any(f in str(c["source"]) for f in filters)]


def main() -> int:
    if not (ROOT / "build" / "kotlin-classes").is_dir() or not os.access(RXC, os.X_OK):
        print("build missing — run `make compiler` first.")
        return 2

    filters: list[str] = []
    verbose = False
    for arg in sys.argv[1:]:
        if arg in ("-v", "--verbose"):
            verbose = True
        elif arg.startswith("-"):
            print(f"unknown option: {arg}")
            print("usage: check_semantic.py [SUBSTR ...] [-v]")
            return 2
        else:
            filters.append(arg)

    cases = select(load_cases(), filters)
    if not cases:
        print(f"no cases matched: {' '.join(filters)}")
        return 2

    print(f"== semantic: {CASE_DIR.relative_to(ROOT)} "
          f"({len(cases)} cases{', filtered' if filters else ''}) ==")
    start = time.time()

    stats: dict[str, dict[str, int]] = {}
    accept_failures: list[str] = []
    reject_backlog: list[str] = []
    accept_total = accept_pass = 0
    reject_total = reject_cleared = 0

    for i, case in enumerate(cases, 1):
        proc = run_rxc(["--stage", "semantic", str(case["source"])])
        rc = None if proc is None else proc.returncode
        s = stats.setdefault(case["dir"], {"at": 0, "ap": 0, "rt": 0, "rr": 0})
        if case["success"]:
            accept_total += 1
            s["at"] += 1
            ok = rc == 0
            if ok:
                accept_pass += 1
                s["ap"] += 1
            else:
                detail = "timeout" if proc is None else f"rc={rc}: {one_line(proc.stderr)}"
                accept_failures.append(f"{case['label']}: expected accept, {detail}")
        else:
            reject_total += 1
            s["rt"] += 1
            ok = rc == 1
            if ok:
                reject_cleared += 1
                s["rr"] += 1
            else:
                detail = ("timeout (a reject must be a normal diagnostic rejection)"
                          if proc is None else
                          "accepted by the compiler" if rc == 0 else
                          f"rc={rc}: {one_line(proc.stderr)}")
                reject_backlog.append(f"{case['label']}: expected reject, {detail}")
        if verbose:
            print(f"  [{'PASS' if ok else 'FAIL'}] {case['label']} (rc={rc})")
            if not ok and proc is not None:
                for line in [l for l in proc.stderr.strip().splitlines() if l][:3]:
                    print(f"    {line}")
        if not filters and i % 50 == 0:
            print(f"  ... {i}/{len(cases)}")

    elapsed = time.time() - start
    print()
    print("  per-directory progress (accept passed/total, reject rejected/total):")
    for name, s in sorted(stats.items(), key=lambda kv: (-(kv[1]["rt"] - kv[1]["rr"]), kv[0])):
        backlog = s["rt"] - s["rr"]
        suffix = f"   <- {backlog} reject left" if backlog else ""
        print(f"    {name:42s} accept {s['ap']}/{s['at']}   reject {s['rr']}/{s['rt']}{suffix}")
    print()
    print(f"  accept: {accept_pass}/{accept_total} passed")
    print(f"  reject: {reject_cleared}/{reject_total} properly rejected")
    if accept_failures:
        print(f"  accept mismatches ({len(accept_failures)}):")
        for item in accept_failures[:MAX_LISTED]:
            print(f"    {item}")
        if len(accept_failures) > MAX_LISTED:
            print(f"    ... {len(accept_failures) - MAX_LISTED} more")
    if reject_backlog and filters:
        print(f"  reject backlog ({len(reject_backlog)}):")
        for item in reject_backlog[:MAX_LISTED]:
            print(f"    {item}")
        if len(reject_backlog) > MAX_LISTED:
            print(f"    ... {len(reject_backlog) - MAX_LISTED} more")
    print(f"  ({elapsed:.1f}s)")

    ok = not accept_failures and not reject_backlog
    if reject_backlog and not accept_failures:
        print(f"SUMMARY: FAIL ({len(reject_backlog)} reject cases not yet rejected; "
              "see the per-directory table)")
    else:
        print("SUMMARY:", "OK" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
