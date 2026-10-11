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

        // Year overview batch: no month filter, but the pay-date gate still applies. Without it
        // the year view would mark a December announcement as received in October.
        check("年度批次接受本月已到派息日的待收记录",
                DividendBatchRules.isEligible("expected", "2026-10-01", asOf), true);
        check("年度批次接受同年更早月份已到派息日的记录",
                DividendBatchRules.isEligible("expected", "2026-01-05", asOf), true);
        check("年度批次拒绝同年更晚月份的未来派息",
                DividendBatchRules.isEligible("expected", "2026-12-20", asOf), false);
        check("年度批次拒绝明年的派息",
                DividendBatchRules.isEligible("expected", "2027-03-01", asOf), false);
        check("年度批次拒绝刚好是明天的派息",
                DividendBatchRules.isEligible("expected", "2026-10-11", asOf), false);
        check("年度批次接受今天到期的派息",
                DividendBatchRules.isEligible("expected", "2026-10-10", asOf), true);
        check("年度批次拒绝已到账记录",
                DividendBatchRules.isEligible("received", "2026-10-01", asOf), false);
        check("年度批次拒绝无效日期",
                DividendBatchRules.isEligible("expected", "2026-02-30", asOf), false);
        check("年度批次拒绝空派息日",
                DividendBatchRules.isEligible("expected", "", asOf), false);
        check("年度批次拒绝空派息日对象",
                DividendBatchRules.isEligible("expected", null, asOf), false);
        check("年度批次拒绝空参照日",
                DividendBatchRules.isEligible("expected", "2026-10-01", (LocalDate) null), false);
        System.out.println("通过：" + assertions + " 项批量到账候选规则断言");
    }
}
