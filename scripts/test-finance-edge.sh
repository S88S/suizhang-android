#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
cleanup() { rm -rf "$OUT"; }
trap cleanup EXIT

# Windows 的 Git Bash 需要显式的 .exe 后缀，否则报command not found
JAVAC="javac"
JAVA="java"
command -v "$JAVAC" >/dev/null 2>&1 || JAVAC="javac.exe"
command -v "$JAVA" >/dev/null 2>&1 || JAVA="java.exe"

"$JAVAC" -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/cn/suizhang/ledger/FinanceMath.java" \
  "$ROOT/scripts/FinanceMathEdgeTest.java"
"$JAVA" -cp "$OUT" cn.suizhang.ledger.FinanceMathEdgeTest
