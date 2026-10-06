#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/cn/suizhang/ledger/DividendForecast.java" \
  "$ROOT/scripts/DividendForecastTest.java"
java -cp "$OUT" cn.suizhang.ledger.DividendForecastTest
