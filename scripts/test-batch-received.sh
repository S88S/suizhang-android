#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
javac -encoding UTF-8 -d "$TMP_DIR" \
  "$ROOT/app/src/main/java/cn/suizhang/ledger/DividendBatchRules.java" \
  "$ROOT/scripts/DividendBatchRulesTest.java"
java -cp "$TMP_DIR" cn.suizhang.ledger.DividendBatchRulesTest
python3 "$ROOT/scripts/test-batch-received.py"
