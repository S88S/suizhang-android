package cn.suizhang.ledger;

import java.util.List;

/**
 * Replays a holding's cost basis from an opening position.
 *
 * <p>Until now holdings.cost was maintained incrementally: each buy, sell and received dividend
 * mutated the stored unit cost once. That is only correct while rows are appended in time order.
 * Editing or deleting a trade in the middle invalidates every later step, so the cost has to be
 * recomputed from the start instead of patched around the edit.
 *
 * <p>Recomputing needs an opening unit cost, which was never stored - only the opening quantity
 * was. {@link #recoverOpening} walks the same events backwards to recover it, which is what the
 * schema 5 migration uses to fill the new column from the numbers a position already has.
 *
 * <p>Trades and received dividends must be interleaved by date rather than processed table by
 * table: a dividend dilutes against the quantity held on the day it landed, not the quantity
 * held today.
 */
final class CostReplay {

    /** One event in the replay: a trade or a received dividend, ordered by date then id. */
    static final class Event {
        final String date;
        final long id;
        /** "买入", "卖出", or "分红". */
        final String kind;
        /** Trade quantity. Unused for dividends, which act on the replayed quantity. */
        final double quantity;
        /** Trade price. Unused for dividends. */
        final double price;
        /** Trade fees. Unused for dividends. */
        final double fees;
        /** Received amount for a dividend. Unused for trades. */
        final double receipt;

        Event(String date, long id, String kind, double quantity, double price, double fees, double receipt) {
            this.date = date; this.id = id; this.kind = kind;
            this.quantity = quantity; this.price = price; this.fees = fees; this.receipt = receipt;
        }
    }

    /** A position: share count and unit cost. */
    static final class State {
        double quantity;
        double cost;
        State(double quantity, double cost) { this.quantity = quantity; this.cost = cost; }
    }

    private CostReplay() {}

    /** Replays events forwards from the opening position. */
    static State replay(State opening, String method, Iterable<Event> events) {
        State s = new State(opening.quantity, opening.cost);
        for (Event e : events) {
            if ("买入".equals(e.kind)) {
                s.cost = FinanceMath.costAfterBuy(method, s.quantity, s.cost, e.quantity, e.price, e.fees);
                s.quantity = s.quantity + e.quantity;
            } else if ("卖出".equals(e.kind)) {
                // A sale with nothing left to sell belongs to the stretch of history that a later
                // close-out erased. Skipping it is safe precisely because costAfterSell resets the
                // basis to zero on a full close: whatever this run computes before that point is
                // discarded, so the replay still lands on the stored numbers.
                double sold = Math.min(e.quantity, Math.max(0d, s.quantity));
                if (sold <= 1e-8) continue;
                s.cost = FinanceMath.costAfterSell(method, s.quantity, s.cost, sold, e.price, e.fees);
                s.quantity = FinanceMath.sellQuantity(s.quantity, sold);
            } else {
                s.cost = FinanceMath.costAfterReceivedDividend(method, s.quantity, s.cost, e.receipt);
            }
        }
        return s;
    }

    /**
     * Recovers the opening position by walking the events backwards from the current one.
     *
     * <p>A sale that closes the position is the one irreversible step: {@code costAfterSell}
     * returns 0 and discards the basis that came before it. That loss is harmless, because a later
     * buy starts from quantity 0 where the previous unit cost contributes nothing at all - so the
     * walk stops there and reports an opening of (0, 0), which replays to the same numbers.
     */
    static State recoverOpening(State current, String method, List<Event> events) {
        State s = new State(current.quantity, current.cost);
        for (int i = events.size() - 1; i >= 0; i--) {
            Event e = events.get(i);
            if ("买入".equals(e.kind)) {
                // Clamp rather than reject: repeated float arithmetic leaves a position that
                // closed out at -1e-14, and a negative opening quantity is not a real state.
                double before = Math.max(0d, s.quantity - e.quantity);
                if (before > 1e-8) s.cost = (s.quantity * s.cost - e.quantity * e.price - e.fees) / before;
                s.quantity = before;
            } else if ("卖出".equals(e.kind)) {
                double before = s.quantity + e.quantity;
                double remaining = s.quantity;
                if (remaining <= 1e-8) return new State(0d, 0d);
                if (FinanceMath.COST_WEIGHTED_AVERAGE.equals(method)) {
                    s.cost = s.cost - e.fees / remaining;
                } else {
                    s.cost = (s.cost * remaining + e.quantity * e.price - e.fees) / before;
                }
                s.quantity = before;
            } else {
                // Only the dividend-adjusted basis dilutes; under the other two a received
                // dividend leaves the unit cost alone, so undoing it here would invent a basis
                // change that never happened.
                if (FinanceMath.COST_DIVIDEND_ADJUSTED.equals(method) && s.quantity > 1e-8) {
                    s.cost = s.cost + e.receipt / s.quantity;
                }
            }
        }
        return s;
    }
}
