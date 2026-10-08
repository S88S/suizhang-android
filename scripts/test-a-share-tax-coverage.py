#!/usr/bin/env python3
"""A-share tax-lot completeness regressions (fixture replay + production wiring guards)."""
from datetime import date
from pathlib import Path
import math
import sqlite3

root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")
db = (root / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java").read_text(encoding="utf-8")
math_src = (root / "app/src/main/java/cn/suizhang/ledger/FinanceMath.java").read_text(encoding="utf-8")
calendar_query = (root / "app/src/main/java/cn/suizhang/ledger/CalendarQuery.java").read_text(encoding="utf-8")
tests = (root / "scripts/FinanceMathTest.java").read_text(encoding="utf-8")

def require(label, ok):
    if not ok:
        raise AssertionError("FAIL " + label)
    print("PASS " + label)

def fixture(initial, opened, transactions, as_of, holding_qty=None):
    """Replay the same opening/ordered-buy/FIFO-sell model using an in-memory SQLite ledger."""
    conn = sqlite3.connect(":memory:")
    conn.executescript("CREATE TABLE holdings(initial_quantity REAL, opened_on TEXT); CREATE TABLE transactions(side TEXT, quantity REAL, trade_date TEXT, id INTEGER PRIMARY KEY);")
    conn.execute("INSERT INTO holdings VALUES(?,?)", (initial, opened or ""))
    conn.executemany("INSERT INTO transactions(side,quantity,trade_date) VALUES(?,?,?)", transactions)
    opened = opened or ""
    as_date = date.fromisoformat(as_of)
    try:
        opening_date = date.fromisoformat(opened) if opened else None
    except ValueError:
        conn.close(); return False, None, None
    if initial < 0 or not math.isfinite(initial):
        conn.close(); return False, None, None
    history_known = initial <= 1e-8 or opening_date is not None
    quantity = initial if opened <= as_of else 0.0
    lots = [[initial, opening_date]] if initial > 0 and opening_date and opened <= as_of else []
    for side, qty, trade_date in conn.execute("SELECT side,quantity,trade_date FROM transactions ORDER BY trade_date,id"):
        try:
            trade_day = date.fromisoformat(trade_date)
        except (TypeError, ValueError):
            conn.close(); return False, None, None
        if side not in ("买入", "卖出") or qty <= 0 or not math.isfinite(qty):
            conn.close(); return False, None, None
        if trade_day <= as_date and opening_date and trade_day < opening_date:
            conn.close(); return False, None, None
        if trade_date > as_of or trade_date < opened:
            continue
        if side == "买入":
            quantity += qty
            lots.append([qty, trade_day])
        else:
            quantity = max(0.0, quantity - qty)
            remaining = qty
            for lot in lots:
                used = min(lot[0], remaining)
                lot[0] -= used
                remaining -= used
                if remaining <= 1e-8:
                    break
            lots = [lot for lot in lots if lot[0] > 1e-8]
    covered = sum(lot[0] for lot in lots)
    if holding_qty is not None and not math.isclose(quantity, holding_qty, rel_tol=1e-12, abs_tol=1e-8):
        conn.close(); return False, quantity, covered
    complete = history_known and quantity > 0 and math.isclose(quantity, covered, rel_tol=1e-12, abs_tol=1e-8) and all(lot[1] <= as_date for lot in lots)
    conn.close()
    return complete, quantity, covered

# Concrete data fixtures: the incomplete opening stake must not be hidden by a dated purchase.
partial = fixture(100, "", [("买入", 50, "2025-07-01")], "2025-08-01")
require("部分已知：未标日期期初 100 + 有日期买入 50，150 预期份额仅 50 可定批，拒绝", partial == (False, 150.0, 50))
require("opened_on 为空且存在初始持仓时拒绝税后估算", fixture(100, "", [], "2025-08-01")[0] is False)
require("opened_on 缺失且存在初始持仓时拒绝税后估算", fixture(100, None, [], "2025-08-01")[0] is False)
known = fixture(100, "2024-06-01", [("买入", 50, "2025-01-01"), ("卖出", 40, "2025-03-01")], "2025-08-01")
require("整笔已知：期初 100 + 买入 50 − FIFO 卖出 40 = 110，税批覆盖完整", known == (True, 110.0, 110.0))
require("无初始存量、空 opened_on 但全由有日期交易形成的 60 股可判完整", fixture(0, "", [("买入", 100, "2025-07-01"), ("卖出", 40, "2025-07-15")], "2025-08-01")[0] is True)
require("日期缺失/无法解析的交易记录拒绝税后估算", fixture(0, "", [("买入", 100, "")], "2025-08-01")[0] is False)
require("当前重放量与账面数量不一致时拒绝", fixture(0, "", [("买入", 100, "2025-07-01")], "2025-08-01", 99)[0] is False)

# Guard that production code—not only this fixture—applies every fail-closed condition.
known_method = main[main.index("private boolean taxEstimateKnown("):main.index("private Map<String, Double> estimatedIncomeByCurrency(")]
rate_method = main[main.index("private double effectiveTaxRate("):main.index("private boolean taxEstimateKnown(")]
coverage_method = db[db.index("boolean hasCompleteTaxLotCoverage("):db.index("private static final class TaxLot", db.index("boolean hasCompleteTaxLotCoverage("))]
require("A 股自动税务已知判断使用覆盖 API 而非任意非空批次", "db.hasCompleteTaxLotCoverage(" in known_method and "taxLotsAt(" not in known_method)
require("有效税率加权前再次要求完整覆盖，未知时不给部分批次税率", "!taxEstimateKnown(h, taxDate)" in rate_method and "return 0d" in rate_method)
require("覆盖 API 按同日 estimatedQuantityAt 与 taxLotsAt 对账", "estimatedQuantityAt(holdingId, asOf.toString())" in coverage_method and "taxLotsAt(holdingId, asOf.toString())" in coverage_method and "hasCompleteTaxLotCoverage(datedLots, expectedQuantity, asOf)" in coverage_method)
require("当前日还要求账面持仓 quantity 一致", "compareHoldingQuantity && !FinanceMath.sameShareQuantity(expectedQuantity, holdingQuantity)" in coverage_method and "date.equals(LocalDate.now())" in known_method)
require("未定日期的正数期初、非 ISO 日期和无效交易不会被忽略", "openingQuantity > 1e-8 && openingDate == null" in coverage_method and "strictIsoDate(tradeDate)" in coverage_method and "date == null || quantity <= 0d" in coverage_method)
require("日期早于 opening checkpoint 的交易不被静默跳过", "date.isBefore(openingDate)" in coverage_method)
require("税率按原始 Lot 加权公式不变，只对完整批次调用", "aShareRecordDateTaxRate(taxLots, date" in rate_method and "double rate = !recordDate.isAfter(lot.acquiredOn.plusMonths(1))" in math_src)
event_row = main[main.index("private void addDividendRow(JSONObject e, boolean allowMarkReceived, String eventKind)"):main.index("private void confirmMarkReceived(")]
require("日历行渲染时按当前 A 股批次重新核验，旧存储 tax_known 不再作为自动税务结论", "taxEstimateKnown(taxHolding, recordDate)" in event_row and "taxRate = taxKnown ? effectiveTaxRate(taxHolding, recordDate) : 0d" in event_row)
require("A 股自定义税率分红仍沿用事件已保存费率", 'taxHolding.put("tax_rate", e.optDouble("tax_rate", 0d))' in event_row)
require("月历和首页未来分红查询都附带持仓市场/税务模式/现量", "h.tax_mode AS holding_tax_mode" in calendar_query and "h.quantity AS holding_quantity" in calendar_query and "h.market AS holding_market,h.tax_mode AS holding_tax_mode,h.quantity AS holding_quantity" in db)
require("覆盖和日期边界测试已接入核心 FinanceMathTest", "hasCompleteTaxLotCoverage" in tests and "超过一个月一天" in tests and "超过一年一天" in tests)
require("数据库仍为 schema 4，没有引入 schema 5", "DB_VERSION = 4" in db and "oldVersion < 5" not in db)
print("通过：A 股税批完整性与边界 fixture/source-wiring 回归")
