package cn.suizhang.ledger;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure date rules behind the daily dividend reminder: which milestone comes next, and whether it
 * falls inside the lookahead window.
 *
 * <p>Free of both Android APIs and org.json. Unit tests run against the android.jar stubs, where
 * JSONObject.optString returns null, so a row-based signature could not be tested at all. The
 * caller reads the three dates out of the row and passes plain strings.
 */
final class DividendMilestones {

    static final int LOOKAHEAD_DAYS = 7;

    private DividendMilestones() { }

    /**
     * Returns a human label when a milestone lands inside the lookahead window, otherwise null.
     * Pay date wins over ex-date, which wins over record date: one row yields one reminder line.
     * Null dates are treated as absent.
     */
    static String milestoneOf(String recordDate, String exDate, String payDate, LocalDate today) {
        if (today == null) return null;
        String pay = payDate == null ? "" : payDate;
        if (pay.length() >= 10 && withinWindow(pay, today)) return "预计 " + pay + " 派息";
        String ex = exDate == null ? "" : exDate;
        if (ex.length() >= 10 && withinWindow(ex, today)) return "预计 " + ex + " 除息";
        String record = recordDate == null ? "" : recordDate;
        if (record.length() >= 10 && withinWindow(record, today)) return "预计 " + record + " 登记日";
        return null;
    }

    /** True when the date is today or up to {@link #LOOKAHEAD_DAYS} days out. Past dates are out. */
    static boolean withinWindow(String isoDate, LocalDate today) {
        if (isoDate == null || today == null) return false;
        try {
            long days = ChronoUnit.DAYS.between(today, LocalDate.parse(isoDate.trim()));
            return days >= 0 && days <= LOOKAHEAD_DAYS;
        } catch (Exception invalidDate) {
            return false;
        }
    }
}
