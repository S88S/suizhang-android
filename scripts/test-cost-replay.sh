#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
javac -encoding UTF-8 -d "$TMP_DIR" \
  "$ROOT/app/src/main/java/cn/suizhang/ledger/FinanceMath.java" \
  "$ROOT/app/src/main/java/cn/suizhang/ledger/CostReplay.java" \
  "$ROOT/scripts/CostReplayTest.java"
java -cp "$TMP_DIR" cn.suizhang.ledger.CostReplayTest
python3 "$ROOT/scripts/test-cost-replay.py"
