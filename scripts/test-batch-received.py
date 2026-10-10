#!/usr/bin/env python3
"""Guard the batch dividend-arrival confirmation.

Filling in a received amount feeds adjustCostForDividend and silently
rewrites holding cost, so the batch path must never write received_amount.
That invariant is behavioural, not stylistic: a batch the user cannot
itemise should not move money.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
db = (root / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java").read_text(encoding="utf-8")
main = (root / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")


def require(label: str, ok: bool) -> None:
    if not ok:
        raise AssertionError("FAIL " + label)
    print("PASS " + label)


batch = db[db.index("int markReceivedBatch("):db.index("void deleteHolding(long id)")]

require("批量核销只写 status，不写 received_amount",
       '"status", "received"' in batch
       and "received_amount" not in batch
       and "adjustCostForDividend" not in batch)

require("批量核销不触发分红摊薄成本（与单笔路径的差别所在）",
       "adjustCostForDividend" in db
       and "adjustCostForDividend" not in batch)

require("批量核销幂等：只改尚未到账的行",
       "_id=? AND status!='received'" in batch)

require("批量核销整体在一个事务内，任一异常不留半截状态",
       "beginTransaction()" in batch
       and "setTransactionSuccessful()" in batch
       and "endTransaction()" in batch)

require("空列表与非法 id 被忽略而非抛错",
       "if (ids == null || ids.isEmpty()) return 0;" in batch
       and "if (id == null || id <= 0) continue;" in batch)

require("UI 入口只在有未到账记录时出现，并展示待确认笔数",
       'actionButton("批量确认到账（"' in main
       and "countPendingReceived(rows)" in main
       and "if (pendingCount > 0)" in main)

require("确认前逐笔列出标的与金额，并提示不会改动成本",
       "confirmMarkReceivedBatch" in main
       and "names.add(label" in main
       and "请先核对券商流水" in main
       and "不会改动持仓成本" in main)

require("批量与单笔共用同一套文案说明，不各说一套",
       "不会触发分红摊薄成本" in main
       and "不会用于分红摊薄成本" in main)

print("通过：批量到账确认的安全约束回归；金额是否补录仍需月历逐笔确认。")