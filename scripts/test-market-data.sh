#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/stubs/android/content"
cat > "$OUT/stubs/android/content/Context.java" <<'JAVA'
package android.content;
public abstract class Context {
    public static final int MODE_PRIVATE = 0;
    public abstract SharedPreferences getSharedPreferences(String name, int mode);
}
JAVA
cat > "$OUT/stubs/android/content/SharedPreferences.java" <<'JAVA'
package android.content;
import java.util.Map;
public interface SharedPreferences {
    String getString(String key, String fallback);
    long getLong(String key, long fallback);
    Map<String, ?> getAll();
    Editor edit();
    interface Editor {
        Editor putString(String key, String value);
        Editor putLong(String key, long value);
        void apply();
    }
}
JAVA
javac -encoding UTF-8 -d "$OUT/classes" \
  "$OUT/stubs/android/content/Context.java" \
  "$OUT/stubs/android/content/SharedPreferences.java" \
  "$ROOT/app/src/main/java/cn/suizhang/ledger/CalendarQuery.java" \
  "$ROOT/app/src/main/java/cn/suizhang/ledger/CalendarYearSummary.java" \
  "$ROOT/app/src/main/java/cn/suizhang/ledger/DividendCalendarRules.java" \
  "$ROOT/app/src/main/java/cn/suizhang/ledger/MarketDataClient.java" \
  "$ROOT/scripts/MarketDataTest.java" \
  "$ROOT/scripts/CalendarQueryTest.java" \
  "$ROOT/scripts/CalendarYearSummaryTest.java"
java -cp "$OUT/classes" cn.suizhang.ledger.MarketDataTest
java -cp "$OUT/classes" cn.suizhang.ledger.CalendarQueryTest
java -cp "$OUT/classes" cn.suizhang.ledger.CalendarYearSummaryTest
java -cp "$OUT/classes" cn.suizhang.ledger.CalendarQueryTest --emit > "$OUT/calendar-query-fixtures.tsv"
python3 "$ROOT/scripts/test-calendar-query.py" "$OUT/calendar-query-fixtures.tsv"
JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"
if [ -x "$ROOT/gradlew" ]; then
  grep -q "idx_dividends_source_key" "$ROOT/app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java"
  grep -q "source_key=?" "$ROOT/app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java"
  grep -q "taxLotsAt" "$ROOT/app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java"
  echo "PASS  分红来源键唯一索引与 FIFO 本地回算结构存在"
fi
