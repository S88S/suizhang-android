package cn.suizhang.ledger;

import java.time.LocalDate;
import java.time.YearMonth;

public final class DividendBatchRulesTest {
    private static int assertions;

    private static void check(String label, boolean actual, boolean expected) {
        assertions++;
        if (actual != expected) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        System.out.println("PASS  " + label);
    }

    public static void main(String[] args) {
        YearMonth october = YearMonth.of(2026, 10);
        LocalDate asOf = LocalDate.of(2026, 10, 10);
        check("本月已到派息日的待收事件可批量确认",
                DividendBatchRules.isEligible("expected", "2026-10-10", october, asOf), true);
        check("本月较早派息的待收事件可批量确认",
                DividendBatchRules.isEligible("expected", "2026-10-01", october, asOf), true);
        check("本月未来派息事件不进入批量确认",
                DividendBatchRules.isEligible("expected", "2026-10-11", october, asOf), false);
        check("相邻月份的记录不进入当前月批次",
                DividendBatchRules.isEligible("expected", "2026-09-30", october, asOf), false);
        check("下一年度的记录不进入当前月批次",
                DividendBatchRules.isEligible("expected", "2027-10-01", october, asOf), false);
        check("已到账记录不重复进入批次",
                DividendBatchRules.isEligible("received", "2026-10-01", october, asOf), false);
        check("无效日期不进入批次",
                DividendBatchRules.isEligible("expected", "2026-02-30", october, asOf), false);
        check("空日期不进入批次",
                DividendBatchRules.isEligible("expected", "", october, asOf), false);
        check("状态不明的记录不进入批次",
                DividendBatchRules.isEligible("unknown", "2026-10-01", october, asOf), false);
        check("允许清理有效 ISO 日期两侧空格",
                DividendBatchRules.isEligible("expected", " 2026-10-01 ", october, asOf), true);
        System.out.println("通过：" + assertions + " 项批量到账候选规则断言");
    }
}
