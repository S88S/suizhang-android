package cn.suizhang.ledger;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/** Eligibility rules for the calendar's local, due-only batch received action. */
final class DividendBatchRules {
    private DividendBatchRules() { }

    static boolean isEligible(String status, String payDate, YearMonth targetMonth, LocalDate asOf) {
        if (!"expected".equals(status) || payDate == null || targetMonth == null || asOf == null) return false;
        try {
            LocalDate payout = LocalDate.parse(payDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            return YearMonth.from(payout).equals(targetMonth) && !payout.isAfter(asOf);
        } catch (DateTimeException invalidDate) {
            return false;
        }
    }
}
