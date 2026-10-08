#!/usr/bin/env python3
"""Offline formula and schema-upgrade checks for the 1.4.4 alignment work."""
from pathlib import Path
import re
import sqlite3
import tempfile

root = Path(__file__).resolve().parents[1]
finance = (root / "app/src/main/java/cn/suizhang/ledger/FinanceMath.java").read_text(encoding="utf-8")
db = (root / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java").read_text(encoding="utf-8")
main = (root / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")

def require(label, ok):
    if not ok:
        raise AssertionError("FAIL " + label)
    print("PASS " + label)

require("成本类覆盖分红摊薄/摊薄/加权平均", all(x in finance for x in ("COST_DIVIDEND_ADJUSTED", "COST_DILUTED", "COST_WEIGHTED_AVERAGE")))
require("卖出费用加入剩余成本", "oldUnitCost * remaining + fees" in finance and "- sellQuantity * sellPrice + fees" in finance)
require("分红摊薄只使用明确的净实收", "actualNetReceipt" in finance and "costAfterReceivedDividend" in db)
require("收益指标含昨收息率/成本息率/浮动盈亏/同币种占比/持股天数", all(x in finance for x in ("marketYield", "costYield", "floatingProfit", "positionWeight", "holdingDays")))
require("税后未知时返回不可用而不沿用税前金额", "if (!taxEstimateKnown(h, today)) return Double.NaN" in main)
require("年度实收汇总优先使用实际到账金额", '"received".equals(e.optString("status")) && !e.isNull("received_amount") ? e.optDouble("received_amount")' in main)
require("外币组合仅按币种分列，不冒充统一组合权重", "同币种占比" in main and "外币汇率折算尚未接入" in main)
require("schema 4 保留加权平均兼容默认和可空实收列", "DB_VERSION = 4" in db and "DEFAULT 'weighted_average'" in db and "received_amount REAL" in db)

upgrade = re.search(r"if \(oldVersion < 4\) \{(.*?)\n        \}", db, re.S)
if not upgrade:
    raise AssertionError("未找到 schema-4 升级分支")
sql = re.findall(r'db\.execSQL\("([^"]+)"\);', upgrade.group(1))
require("schema-4 有且只有不重写旧账值的两项 ALTER", len(sql) == 2 and all(x.startswith("ALTER TABLE") for x in sql))
with tempfile.NamedTemporaryFile(suffix=".sqlite") as f:
    conn = sqlite3.connect(f.name)
    conn.execute("CREATE TABLE holdings (_id INTEGER PRIMARY KEY, quantity REAL, cost REAL)")
    conn.execute("CREATE TABLE dividends (_id INTEGER PRIMARY KEY, total REAL)")
    conn.execute("INSERT INTO holdings VALUES(1, 20, 12.345)")
    conn.execute("INSERT INTO dividends VALUES(1, 45.67)")
    for statement in sql:
        conn.execute(statement)
    holding = conn.execute("SELECT quantity,cost,cost_method FROM holdings WHERE _id=1").fetchone()
    dividend = conn.execute("SELECT total,received_amount FROM dividends WHERE _id=1").fetchone()
    require("旧持仓数量/成本保留且算法默认为加权平均", holding == (20.0, 12.345, "weighted_average"))
    require("旧分红原金额保留，实际到账留空", dividend == (45.67, None))
    conn.close()
print("通过：schema-4 升级和公式边界离线回归")
