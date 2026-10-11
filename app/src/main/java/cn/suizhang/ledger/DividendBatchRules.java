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

    /**
     * Same rule without a month filter, for the year overview batch.
     *
     * <p>The overview lists a whole year, so a batch there would otherwise sweep in December
     * announcements whose pay date has not arrived yet. Reusing the same predicate keeps both
     * entry points from marking a future payout as received.
     */
    static boolean isEligible(String status, String payDate, LocalDate asOf) {
        if (!"expected".equals(status) || payDate == null || asOf == null) return false;
        try {
            LocalDate payout = LocalDate.parse(payDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            return !payout.isAfter(asOf);
        } catch (DateTimeException invalidDate) {
            return false;
        }
    }
}
