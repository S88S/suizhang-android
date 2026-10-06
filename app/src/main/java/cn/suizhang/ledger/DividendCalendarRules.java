package cn.suizhang.ledger;

import java.util.ArrayList;

/** UI-neutral calendar rules shared by the ledger and JVM regression tests. */
final class DividendCalendarRules {
    private DividendCalendarRules() {}

    static String kindsForDate(String recordDate, String exDate, String payDate, String selectedDate) {
        ArrayList<String> labels = new ArrayList<>();
        if (sameDate(recordDate, selectedDate)) labels.add("登记日");
        if (sameDate(exDate, selectedDate)) labels.add("除息日");
        if (sameDate(payDate, selectedDate)) labels.add("派息日");
        return join(labels, " / ");
    }

    static String statusLabel(String dataClass, String status) {
        if ("received".equals(status)) return "已到账";
        if ("announced".equals(dataClass)) return "已公告";
        if ("historical_estimate".equals(dataClass)) return "历史推算";
        if ("manual".equals(dataClass)) return "手工记录";
        return "状态未知";
    }

    static String eventIdentity(String code, String period, String recordDate, String exDate) {
        return clean(code) + "|" + clean(period) + "|" + clean(recordDate) + "|" + clean(exDate);
    }

    private static boolean sameDate(String a, String b) {
        return a != null && b != null && a.length() >= 10 && b.length() >= 10
                && a.substring(0, 10).equals(b.substring(0, 10));
    }
    private static String join(ArrayList<String> values, String separator) {
        StringBuilder out = new StringBuilder();
        for (String s : values) { if (out.length() > 0) out.append(separator); out.append(s); }
        return out.toString();
    }
    private static String clean(String value) { return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT); }
}
