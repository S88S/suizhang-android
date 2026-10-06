package cn.suizhang.ledger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure, auditable historical dividend estimate logic shared by the form and regression tests. */
final class DividendForecast {
    private static final Pattern YEAR = Pattern.compile("(?:19|20)\\d{2}");

    private DividendForecast() { }

    static final class Report {
        final String period;
        final double perShare;

        Report(String period, double perShare) {
            this.period = period == null ? "" : period;
            this.perShare = perShare;
        }
    }

    static final class Estimate {
        final boolean hasData;
        final double annualPerUnit;
        final int years;
        final int firstYear;
        final int lastYear;
        final int yearsWithReports;

        private Estimate(boolean hasData, double annualPerUnit, int years, int firstYear,
                         int lastYear, int yearsWithReports) {
            this.hasData = hasData;
            this.annualPerUnit = annualPerUnit;
            this.years = years;
            this.firstYear = firstYear;
            this.lastYear = lastYear;
            this.yearsWithReports = yearsWithReports;
        }

        static Estimate unavailable(int years) {
            return new Estimate(false, 0d, years, 0, 0, 0);
        }
    }

    /**
     * A 1/3/5-year mean over completed reporting years, ending at currentYear - 1.
     * Multiple interim/final distributions in one year are added before averaging.
     * Missing report years remain zero in the denominator; no matched history is unavailable,
     * not a zero-yield forecast.
     */
    static Estimate calculate(List<Report> reports, int years, int currentYear) {
        if (years != 1 && years != 3 && years != 5) {
            throw new IllegalArgumentException("历史窗口仅支持 1、3 或 5 年");
        }
        int lastYear = currentYear - 1;
        int firstYear = lastYear - years + 1;
        Map<Integer, Double> totalsByYear = new HashMap<>();
        if (reports != null) {
            for (Report report : reports) {
                if (report == null || !Double.isFinite(report.perShare) || report.perShare <= 0d) continue;
                int reportYear = extractYear(report.period);
                if (reportYear < firstYear || reportYear > lastYear) continue;
                totalsByYear.put(reportYear, totalsByYear.getOrDefault(reportYear, 0d) + report.perShare);
            }
        }
        if (totalsByYear.isEmpty()) return Estimate.unavailable(years);
        double total = 0d;
        for (double yearlyTotal : totalsByYear.values()) total += yearlyTotal;
        if (!Double.isFinite(total) || total <= 0d) return Estimate.unavailable(years);
        return new Estimate(true, total / years, years, firstYear, lastYear, totalsByYear.size());
    }

    static int extractYear(String period) {
        if (period == null) return -1;
        Matcher matcher = YEAR.matcher(period);
        if (!matcher.find()) return -1;
        try { return Integer.parseInt(matcher.group()); }
        catch (NumberFormatException ignored) { return -1; }
    }

    /** Returns NaN instead of a misleading zero until both history and a positive quantity exist. */
    static double annualTotal(double quantity, Estimate estimate) {
        if (estimate == null || !estimate.hasData || !Double.isFinite(quantity) || quantity <= 0d)
            return Double.NaN;
        return quantity * estimate.annualPerUnit;
    }
}
