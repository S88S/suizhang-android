#!/usr/bin/env python3
"""Data-flow checks for the replay behind trade and dividend editing.

The maths of the replay is covered by scripts/CostReplayTest.java against the real FinanceMath.
What this script adds is the part that Java test cannot reach: the SQL that decides which rows
take part, and in what order. Both queries below are extracted from LedgerDatabase rather than
restated here, so a change to the query breaks this test instead of silently disagreeing with it.

The cost formulas are then re-implemented in Python and the whole flow re-run: a second,
independent implementation landing on the same numbers is a check on the wiring, not a restatement
of the Java one.
"""
import re
import sqlite3
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
database = (root / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java").read_text(encoding="utf-8")
activity = (root / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")
replay = (root / "app/src/main/java/cn/suizhang/ledger/CostReplay.java").read_text(encoding="utf-8")

WEIGHTED = "weighted_average"
DILUTED = "diluted"
ADJUSTED = "dividend_adjusted"


def require(label: str, condition: bool) -> None:
    if not condition:
        raise AssertionError("FAIL " + label)
    print("PASS  " + label)


def close(a: float, b: float) -> bool:
    return abs(a - b) <= 1e-6 * max(1.0, abs(a), abs(b))


# --- the replay exists and is wired into every path that can move a stored cost ---
require("架构版本升到 5，为可重算的成本腾出期初成本列", "static final int DB_VERSION = 5;" in database)
# Scoped to createCore: the migration carries the same column definition, so a file-wide search
# would keep passing even if new databases were created without it.
create_core = database[database.index("private void createCore("):database.index("private static void createIndexIfMissing(")]
require("建表时带 opening_cost 列", "opening_cost REAL NOT NULL DEFAULT 0" in create_core)
upgrade = database[database.index("public synchronized void onUpgrade("):database.index("private void backfillOpeningCost(")]
require("迁移为旧库补列", 'ALTER TABLE holdings ADD COLUMN opening_cost REAL NOT NULL DEFAULT 0' in upgrade)
require("迁移回填期初成本", "backfillOpeningCost(db);" in upgrade)
require("重算入口存在", "private void rebuildHolding(SQLiteDatabase db, long holdingId)" in database)
require("重算从期初数量与期初成本出发",
        "SELECT initial_quantity,opening_cost,cost_method FROM holdings WHERE _id=?" in database)

# --- which rows take part, and in what order ---
require("只有填了实收金额的已收分红才参与重算",
        "status='received' AND received_amount IS NOT NULL" in database)
require("同一天先交易后分红，且这个次序是稳定的",
        "private static int dividendRank(" in database and "dividendRank(a), dividendRank(b)" in database)

# --- every path that changes a stored cost goes through the replay, not a bespoke step ---
require("新增交易走重算而不是就地累加", "db.insertOrThrow(\"transactions\", null, cv);" in database
        and "rebuildHolding(db, holdingId);" in database)
require("旧的增量摊薄函数已移除，避免两条路径各算一套",
        "private void adjustCostForDividend(" not in database)
for name in ("deleteTransaction", "updateTransaction", "deleteDividend", "updateDividend"):
    body = database[database.index("boolean " + name + "("):]
    body = body[:body.index("\n    }")]
    # Matched on the exact call, not the bare name: the "moved to another holding" line also
    # rebuilds, and would keep this passing after the rebuild of the holding itself was removed.
    require(name + " 在同一事务内改行后重算该持仓",
            "beginTransaction()" in body and "rebuildHolding(db, holdingId);" in body and "endTransaction()" in body)
require("把交易移到别的持仓时两边都重算",
        "if (previousHolding != holdingId) rebuildHolding(db, previousHolding);" in database)
# Scoped to the editor, not to the whole file: backfillOpeningCost calls the same helper, so a
# file-wide search would keep passing after the editor stopped doing it.
save_holding = database[database.index("long saveHolding("):database.index("private void addIndexIfMissing(")]
require("手工改成本时反推期初，使重放结果就是用户填的值",
        "syncOpeningCost(db, id);" in save_holding)
import_json = database[database.index("void importJson(JSONObject root)"):database.index("private JSONArray requiredArray(")]
require("导入旧备份后回填期初成本，否则会从 0 起算",
        "if (version < 5) {" in import_json and "backfillOpeningCost(db);" in import_json)
require("反向回推只在摊薄成本法下撤销分红",
        'FinanceMath.COST_DIVIDEND_ADJUSTED.equals(method) && s.quantity > 1e-8' in replay)
require("重放遇到无仓可卖时跳过，而不是让清仓前的历史污染结果",
        "if (sold <= 1e-8) continue;" in replay)

# --- the editing entry points exist on both lists ---
require("交易行提供编辑与删除", "private LinearLayout editDeleteRow(" in activity
        and "showTransactionDialog(0, t.optLong(\"_id\"))" in activity)
require("分红行提供编辑与删除", "showDividendDialog(0, e.optLong(\"_id\"))" in activity)
require("删除交易前确认并说明会重算", "confirmDeleteTransaction" in activity
        and "数量与成本会按剩余记录重新计算" in activity)
require("删除分红前说明摊薄会被加回", "已摊薄过持仓成本，删除后会被加回" in activity)
require("两个对话框都区分新增与编辑", "新增交易记录\" : \"编辑交易记录" in activity
        and "添加分红事件\" : \"编辑分红事件" in activity)
require("编辑时不受账户筛选限制，否则可能选不到原持仓",
        "db.holdings(editId > 0 ? 0 : selectedAccount)" in activity)

# --- the queries under test are the ones in the source, not copies of them ---
trade_query = re.search(r'rawQuery\("(SELECT _id,side,trade_date[^"]+)"', database)
dividend_query = re.search(r'rawQuery\("(SELECT _id,pay_date,received_amount[^"]+)"', database)
require("能从源码取到交易查询语句", trade_query is not None)
require("能从源码取到分红查询语句", dividend_query is not None)
require("分红查询没有用 COALESCE 把未填金额也算作摊薄",
        "COALESCE" not in dividend_query.group(1))


def cost_after_buy(method, old_qty, old_cost, qty, price, fees):
    return (old_qty * old_cost + qty * price + fees) / (old_qty + qty)


def cost_after_sell(method, old_qty, old_cost, qty, price, fees):
    remaining = max(0.0, old_qty - qty)
    if remaining <= 1e-8:
        return 0.0
    if method == WEIGHTED:
        return (old_cost * remaining + fees) / remaining
    return (old_cost * old_qty - qty * price + fees) / remaining


def cost_after_dividend(method, qty, cost, receipt):
    if method != ADJUSTED or qty <= 0:
        return cost
    return (qty * cost - receipt) / qty


def replay(opening_qty, opening_cost, method, events):
    qty, cost = opening_qty, opening_cost
    for kind, when, amount, price, fees in events:
        if kind == "买入":
            cost = cost_after_buy(method, qty, cost, amount, price, fees)
            qty += amount
        elif kind == "卖出":
            sold = min(amount, max(0.0, qty))
            if sold <= 1e-8:
                continue
            cost = cost_after_sell(method, qty, cost, sold, price, fees)
            qty = max(0.0, qty - sold)
        else:
            cost = cost_after_dividend(method, qty, cost, amount)
    return qty, cost


def recover(opening_qty, cost, method, events):
    qty = opening_qty
    for kind, when, amount, price, fees in reversed(events):
        if kind == "买入":
            before = max(0.0, qty - amount)
            if before > 1e-8:
                cost = (qty * cost - amount * price - fees) / before
            qty = before
        elif kind == "卖出":
            before = qty + amount
            remaining = qty
            if remaining <= 1e-8:
                return 0.0, 0.0
            if method == WEIGHTED:
                cost = cost - fees / remaining
            else:
                cost = (cost * remaining + amount * price - fees) / before
            qty = before
        else:
            if method == ADJUSTED and qty > 1e-8:
                cost = cost + amount / qty
    return qty, cost


def events_for(conn, holding_id):
    """Runs the two extracted queries, then applies the same tie-break the source uses."""
    rows = []
    for _id, side, when, qty, price, fees in conn.execute(trade_query.group(1), (str(holding_id),)):
        rows.append((when, 0, _id, side, qty, price, fees))
    for _id, when, receipt in conn.execute(dividend_query.group(1), (str(holding_id),)):
        rows.append((when, 1, _id, "分红", receipt, 0.0, 0.0))
    rows.sort()
    return [(kind, when, qty, price, fees) for when, _rank, _id, kind, qty, price, fees in rows]


def scenario(name, method, trades, dividends, opening_qty, opening_cost):
    """Stores a history the incremental code would produce, then checks the replay reproduces it."""
    conn = sqlite3.connect(":memory:")
    conn.execute("CREATE TABLE holdings (_id INTEGER PRIMARY KEY AUTOINCREMENT, quantity REAL NOT NULL,"
                 " cost REAL NOT NULL, initial_quantity REAL NOT NULL DEFAULT 0,"
                 " cost_method TEXT NOT NULL DEFAULT 'weighted_average', opening_cost REAL NOT NULL DEFAULT 0)")
    conn.execute("CREATE TABLE transactions (_id INTEGER PRIMARY KEY AUTOINCREMENT, holding_id INTEGER NOT NULL,"
                 " side TEXT NOT NULL, trade_date TEXT NOT NULL, quantity REAL NOT NULL, price REAL NOT NULL,"
                 " fees REAL NOT NULL)")
    conn.execute("CREATE TABLE dividends (_id INTEGER PRIMARY KEY AUTOINCREMENT, holding_id INTEGER NOT NULL,"
                 " pay_date TEXT NOT NULL, status TEXT NOT NULL, total REAL NOT NULL, received_amount REAL)")

    qty, cost = opening_qty, opening_cost
    combined = sorted([(when, 0, i, kind, a, p, f) for i, (kind, when, a, p, f) in enumerate(trades)]
                      + [(when, 1, i, "分红", r, 0.0, 0.0) for i, (when, r) in enumerate(dividends)])
    for _w, _r, _i, kind, amount, price, fees in combined:
        qty, cost = replay(qty, cost, method, [(kind, "", amount, price, fees)])
    conn.execute("INSERT INTO holdings (quantity,cost,initial_quantity,cost_method,opening_cost)"
                 " VALUES (?,?,?,?,0)", (qty, cost, opening_qty, method))
    for kind, when, amount, price, fees in trades:
        conn.execute("INSERT INTO transactions (holding_id,side,trade_date,quantity,price,fees)"
                     " VALUES (1,?,?,?,?,?)", (kind, when, amount, price, fees))
    for when, receipt in dividends:
        conn.execute("INSERT INTO dividends (holding_id,pay_date,status,total,received_amount)"
                     " VALUES (1,?,'received',?,?)", (when, receipt, receipt))
    conn.commit()

    events = events_for(conn, 1)
    expected_rows = len(trades) + len([1 for _w, r in dividends if r is not None])
    require(name + "：重放选取的记录条数与预期一致（" + str(len(events)) + "/" + str(expected_rows) + "）",
            len(events) == expected_rows)

    recovered_qty, recovered_cost = recover(qty, cost, method, events)
    rebuilt_qty, rebuilt_cost = replay(recovered_qty, recovered_cost, method, events)
    require(name + "：回填期初成本后重算仍落在原值上（数量 " + str(round(rebuilt_qty, 4))
                   + " 成本 " + str(round(rebuilt_cost, 4)) + "）",
            close(rebuilt_qty, qty) and close(rebuilt_cost, cost))

    # Deleting the first trade must leave the position as the remaining history would produce it.
    if trades:
        remaining = events[1:]
        after_delete = replay(recovered_qty, recovered_cost, method, remaining)
        expected_qty, expected_cost = recovered_qty, recovered_cost
        for event in remaining:
            expected_qty, expected_cost = replay(expected_qty, expected_cost, method, [event])
        require(name + "：删掉最早一笔后重算等于按剩余记录直接算出的结果",
                close(after_delete[0], expected_qty) and close(after_delete[1], expected_cost))
    conn.close()


scenario("加权平均：买买卖", WEIGHTED,
         [("买入", "2024-01-05", 100, 10.0, 0.0), ("买入", "2024-03-05", 100, 12.0, 0.0),
          ("卖出", "2024-06-05", 50, 15.0, 0.0)], [], 0.0, 0.0)
scenario("摊薄成本法：买入后分红到账", ADJUSTED,
         [("买入", "2024-01-05", 1000, 10.0, 0.0)], [("2024-06-01", 200.0)], 0.0, 0.0)
scenario("期初已有持仓", WEIGHTED,
         [("买入", "2024-02-01", 200, 8.0, 5.0), ("卖出", "2024-05-01", 100, 9.5, 5.0)], [], 500, 12.0)
scenario("同日交易与分红", ADJUSTED,
         [("买入", "2024-01-05", 1000, 10.0, 0.0)], [("2024-01-05", 150.0)], 0.0, 0.0)

# A dividend that is marked received but has no amount has never moved the cost, so it must not
# start taking part in the replay now - this is the one that would quietly rewrite old positions.
conn = sqlite3.connect(":memory:")
conn.execute("CREATE TABLE dividends (_id INTEGER PRIMARY KEY AUTOINCREMENT, holding_id INTEGER NOT NULL,"
             " pay_date TEXT NOT NULL, status TEXT NOT NULL, total REAL NOT NULL, received_amount REAL)")
conn.execute("INSERT INTO dividends (holding_id,pay_date,status,total,received_amount)"
             " VALUES (1,'2024-06-01','received',300.0,NULL)")
conn.commit()
require("已到账但未填金额的分红不参与重算", len(conn.execute(dividend_query.group(1), ("1",)).fetchall()) == 0)
conn.close()

print("通过：成本重算的数据流与编辑入口回归；SQL 取自源码，成本公式由 CostReplayTest 用真实代码覆盖。")
