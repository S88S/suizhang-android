package cn.suizhang.ledger;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Pure year/month/currency aggregation for the dividend calendar; never converts or combines currencies. */
final class CalendarYearSummary {
    static final class Event {
        final String payDate;
        final String currency;
        final String status;
        final double amount;

        Event(String payDate, String currency, String status, double amount) {
            this.payDate = payDate;
            this.currency = currency;
            this.status = status;
            this.amount = amount;
        }
    }

    static final class Amounts {
        double received;
        double estimated;
    }

    static final class Month {
        private final Map<String, Amounts> amounts = new TreeMap<>();
        private int eventCount;

        Amounts amounts(String currency) { return amounts.get(currency); }
        java.util.Set<String> currencyCodes() { return Collections.unmodifiableSet(amounts.keySet()); }
        int eventCount() { return eventCount; }
    }

    final int year;
    private final Month[] months = new Month[12];

    private CalendarYearSummary(int year) {
        this.year = year;
        for (int i = 0; i < months.length; i++) months[i] = new Month();
    }

    static boolean isSupportedYear(long year) { return year >= 1 && year <= 9999; }

    static CalendarYearSummary calculate(int year, Iterable<Event> events) {
        if (!isSupportedYear(year)) throw new IllegalArgumentException("year must be between 1 and 9999");
        CalendarYearSummary summary = new CalendarYearSummary(year);
        if (events == null) return summary;
        for (Event event : events) {
            if (event == null || event.payDate == null) continue;
            final LocalDate payDate;
            try { payDate = LocalDate.parse(event.payDate.trim()); }
            catch (DateTimeParseException | NullPointerException ignored) { continue; }
            if (payDate.getYear() != year || !Double.isFinite(event.amount) || event.amount < 0d) continue;
            String currency = event.currency == null || event.currency.trim().isEmpty() ? "CNY" : event.currency.trim();
            Month month = summary.months[payDate.getMonthValue() - 1];
            Amounts amounts = month.amounts.computeIfAbsent(currency, ignored -> new Amounts());
            if ("received".equals(event.status)) amounts.received += event.amount;
            else amounts.estimated += event.amount;
            month.eventCount++;
        }
        return summary;
    }

    Month month(int month) {
        if (month < 1 || month > 12) throw new IllegalArgumentException("month must be between 1 and 12");
        return months[month - 1];
    }

    boolean hasEvents() {
        for (Month month : months) if (month.eventCount > 0) return true;
        return false;
    }

    Map<String, Double> receivedByCurrency() { return totalsByCurrency(true); }
    Map<String, Double> estimatedByCurrency() { return totalsByCurrency(false); }

    double maxMonthlyTotal(String currency) {
        double max = 0d;
        for (Month month : months) {
            Amounts value = month.amounts(currency);
            if (value != null) max = Math.max(max, value.received + value.estimated);
        }
        return max;
    }

    private Map<String, Double> totalsByCurrency(boolean received) {
        Map<String, Double> totals = new TreeMap<>();
        for (Month month : months) for (Map.Entry<String, Amounts> entry : month.amounts.entrySet()) {
            double value = received ? entry.getValue().received : entry.getValue().estimated;
            if (value > 0d) totals.put(entry.getKey(), totals.getOrDefault(entry.getKey(), 0d) + value);
        }
        return totals;
    }
}
