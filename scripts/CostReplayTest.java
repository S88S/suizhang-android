package cn.suizhang.ledger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Checks the cost replay that editing and deleting a trade depends on.
 *
 * <p>holdings.cost used to be maintained one step at a time: each buy, sell and received dividend
 * mutated the stored unit cost as it happened. That is only correct while rows are appended in
 * time order. Editing or deleting one in the middle invalidates every step after it, which is why
 * the app now recomputes the whole position from an opening checkpoint.
 *
 * <p>Two claims have to hold, and both are checked here against the real FinanceMath:
 *
 * <ol>
 *   <li>replaying from the opening reproduces the incrementally maintained numbers, so switching
 *       to a replay does not silently rewrite any position that already exists;</li>
 *   <li>the opening can be recovered by walking the same events backwards, which is what the
 *       schema 5 migration does, since the opening cost was never stored.</li>
 * </ol>
 */
public final class CostReplayTest {
    private static int assertions;

    public static void main(String[] args) {
        replayMatchesIncremental();
        openingIsRecoverable();
        deletingATradeReplaysWhatIsLeft();
        deletingADilutingDividendRestoresTheBasis();
        nonDilutingMethodsIgnoreDividends();
        System.out.println("通过：" + assertions + " 项成本重放断言");
    }

    // ---------------------------------------------------------------- cases

    /**
     * The claim that protects existing data: for a history the app could have produced, replaying
     * it must land on exactly the numbers the incremental code already stored.
     */
    private static void replayMatchesIncremental() {
        String[] methods = {FinanceMath.COST_WEIGHTED_AVERAGE, FinanceMath.COST_DILUTED,
                FinanceMath.COST_DIVIDEND_ADJUSTED};
        for (String method : methods) {
            Random random = new Random(20261013L + method.hashCode());
            int cases = 0, matched = 0;
            for (int trial = 0; trial < 4000; trial++) {
                Scenario s = Scenario.random(random, method);
                if (s == null) continue;
                cases++;
                CostReplay.State replayed = CostReplay.replay(s.opening, method, s.events);
                if (close(replayed.quantity, s.incremental.quantity)
                        && close(replayed.cost, s.incremental.cost)) matched++;
            }
            check(method + "：正向重放与增量维护结果一致（" + matched + "/" + cases + "）",
                    cases > 0 && matched == cases, true);
        }
    }

    /** The migration depends on this: opening_cost can only be filled in if it can be recovered. */
    private static void openingIsRecoverable() {
        String[] methods = {FinanceMath.COST_WEIGHTED_AVERAGE, FinanceMath.COST_DILUTED,
                FinanceMath.COST_DIVIDEND_ADJUSTED};
        for (String method : methods) {
            Random random = new Random(20261013L + method.hashCode());
            int cases = 0, matched = 0;
            for (int trial = 0; trial < 4000; trial++) {
                Scenario s = Scenario.random(random, method);
                if (s == null) continue;
                cases++;
                CostReplay.State recovered = CostReplay.recoverOpening(s.incremental, method, s.events);
                CostReplay.State replayed = CostReplay.replay(recovered, method, s.events);
                if (close(replayed.quantity, s.incremental.quantity)
                        && close(replayed.cost, s.incremental.cost)) matched++;
            }
            check(method + "：反向回推期初后重放仍一致（" + matched + "/" + cases + "）",
                    cases > 0 && matched == cases, true);
        }

        // A closed-out position is the one place the backwards walk cannot see past, and the place
        // where a wrong answer would stay invisible until somebody deletes an old trade.
        List<CostReplay.Event> events = new ArrayList<>();
        events.add(new CostReplay.Event("2024-01-05", 1, "买入", 100, 10d, 5d, 0));
        events.add(new CostReplay.Event("2024-06-01", 2, "卖出", 100, 12d, 5d, 0));
        events.add(new CostReplay.Event("2024-09-01", 3, "买入", 50, 11d, 2d, 0));
        CostReplay.State incremental = new CostReplay.State(0d, 0d);
        incremental.cost = FinanceMath.costAfterBuy(FinanceMath.COST_DILUTED, 0, 0, 100, 10d, 5d);
        incremental.quantity = 100d;
        incremental.cost = FinanceMath.costAfterSell(FinanceMath.COST_DILUTED, 100, incremental.cost, 100, 12d, 5d);
        incremental.quantity = FinanceMath.sellQuantity(100, 100);
        incremental.cost = FinanceMath.costAfterBuy(FinanceMath.COST_DILUTED, 0, incremental.cost, 50, 11d, 2d);
        incremental.quantity = 50d;
        CostReplay.State replayed = CostReplay.replay(
                CostReplay.recoverOpening(incremental, FinanceMath.COST_DILUTED, events),
                FinanceMath.COST_DILUTED, events);
        check("清仓再建仓：回推停在清仓点，重放结果与增量一致",
                close(replayed.quantity, 50d) && close(replayed.cost, incremental.cost), true);
    }

    /**
     * What deleting a trade actually has to do: the position must end up as though the removed
     * trade had never been recorded, not merely with something subtracted from it.
     */
    private static void deletingATradeReplaysWhatIsLeft() {
        CostReplay.Event first = new CostReplay.Event("2024-01-05", 1, "买入", 100, 10d, 0d, 0);
        CostReplay.Event middle = new CostReplay.Event("2024-03-05", 2, "买入", 100, 12d, 0d, 0);
        CostReplay.Event sale = new CostReplay.Event("2024-06-05", 3, "卖出", 50, 15d, 0d, 0);
        String method = FinanceMath.COST_WEIGHTED_AVERAGE;

        List<CostReplay.Event> all = new ArrayList<>();
        all.add(first); all.add(middle); all.add(sale);
        double withMiddle = FinanceMath.costAfterSell(method, 200,
                FinanceMath.costAfterBuy(method, 100,
                        FinanceMath.costAfterBuy(method, 0, 0, 100, 10d, 0d), 100, 12d, 0d),
                50, 15d, 0d);

        List<CostReplay.Event> withoutMiddle = new ArrayList<>();
        withoutMiddle.add(first); withoutMiddle.add(sale);
        CostReplay.State afterDelete = CostReplay.replay(new CostReplay.State(0d, 0d), method, withoutMiddle);
        double expected = FinanceMath.costAfterSell(method, 100,
                FinanceMath.costAfterBuy(method, 0, 0, 100, 10d, 0d), 50, 15d, 0d);

        check("删除中间的买入后，重放等于按剩余记录直接算出的成本（"
                        + withMiddle + " → " + afterDelete.cost + "）",
                close(afterDelete.cost, expected) && close(afterDelete.quantity, 50d), true);
        check("保留该买入时成本确实不同，删除不是空操作", withMiddle != expected, true);
    }

    /** The dividend case: a received dividend that diluted the basis has to give it back. */
    private static void deletingADilutingDividendRestoresTheBasis() {
        String method = FinanceMath.COST_DIVIDEND_ADJUSTED;
        List<CostReplay.Event> withDividend = new ArrayList<>();
        withDividend.add(new CostReplay.Event("2024-01-05", 1, "买入", 1000, 10d, 0d, 0));
        withDividend.add(new CostReplay.Event("2024-06-01", 2, "分红", 0, 0, 0, 200d));
        List<CostReplay.Event> withoutDividend = withDividend.subList(0, 1);

        CostReplay.State opening = new CostReplay.State(0d, 0d);
        CostReplay.State diluted = CostReplay.replay(opening, method, withDividend);
        CostReplay.State restored = CostReplay.replay(opening, method, withoutDividend);
        check("分红摊薄后成本低于买入价（" + diluted.cost + "）", diluted.cost < 10d, true);
        check("删除该分红后成本回到摊薄前（" + diluted.cost + " → " + restored.cost + "）",
                close(restored.cost, 10d), true);

        // And the recovery has to survive the round trip, or the migration would lose it.
        CostReplay.State recovered = CostReplay.recoverOpening(diluted, method, withDividend);
        check("摊薄后的期初成本可被反推回来",
                close(CostReplay.replay(recovered, method, withDividend).cost, diluted.cost), true);
    }

    /** Only the dividend-adjusted basis moves the unit cost; the other two must not. */
    private static void nonDilutingMethodsIgnoreDividends() {
        for (String method : new String[]{FinanceMath.COST_WEIGHTED_AVERAGE, FinanceMath.COST_DILUTED}) {
            List<CostReplay.Event> events = new ArrayList<>();
            events.add(new CostReplay.Event("2024-01-05", 1, "买入", 1000, 10d, 0d, 0));
            events.add(new CostReplay.Event("2024-06-01", 2, "分红", 0, 0, 0, 200d));
            CostReplay.State replayed = CostReplay.replay(new CostReplay.State(0d, 0d), method, events);
            check(method + "：分红不改变每份成本（" + replayed.cost + "）", close(replayed.cost, 10d), true);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** A generated history plus the numbers the incremental code would have stored for it. */
    private static final class Scenario {
        final CostReplay.State opening;
        final List<CostReplay.Event> events;
        final CostReplay.State incremental;

        Scenario(CostReplay.State opening, List<CostReplay.Event> events, CostReplay.State incremental) {
            this.opening = opening; this.events = events; this.incremental = incremental;
        }

        /**
         * Events are generated, sorted by date then id, then replayed in that order to produce the
         * incremental numbers. Sorting first is the point: the app reads them with ORDER BY, so the
         * check has to hold for that order and not for the order they happened to be generated in.
         */
        static Scenario random(Random random, String method) {
            double openingQuantity = random.nextBoolean() ? 0d : 100 * (1 + random.nextInt(9));
            double openingCost = openingQuantity > 0 ? 5 + random.nextDouble() * 20 : 0d;
            List<CostReplay.Event> events = new ArrayList<>();
            double quantity = openingQuantity;
            long id = 1;
            String date = "2024-01-01";
            int count = 1 + random.nextInt(6);
            for (int i = 0; i < count; i++) {
                date = plusDays(date, random);
                if (quantity <= 1e-8 || random.nextBoolean()) {
                    double q = 10 * (1 + random.nextInt(20));
                    events.add(new CostReplay.Event(date, id++, "买入", q, 5 + random.nextDouble() * 20,
                            random.nextDouble() * 10, 0));
                    quantity += q;
                } else {
                    double q = Math.min(Math.max(1d, quantity * (0.2 + random.nextDouble() * 0.8)), quantity);
                    events.add(new CostReplay.Event(date, id++, "卖出", q, 5 + random.nextDouble() * 20,
                            random.nextDouble() * 10, 0));
                    quantity -= q;
                }
                if (quantity > 1e-8 && random.nextInt(3) == 0) {
                    date = plusDays(date, random);
                    events.add(new CostReplay.Event(date, id++, "分红", 0, 0, 0, random.nextDouble() * 100));
                }
            }
            events.sort((a, b) -> {
                int byDate = a.date.compareTo(b.date);
                return byDate != 0 ? byDate : Long.compare(a.id, b.id);
            });

            double cost = openingCost;
            quantity = openingQuantity;
            for (CostReplay.Event e : events) {
                try {
                    if ("买入".equals(e.kind)) {
                        cost = FinanceMath.costAfterBuy(method, quantity, cost, e.quantity, e.price, e.fees);
                        quantity += e.quantity;
                    } else if ("卖出".equals(e.kind)) {
                        double sold = Math.min(e.quantity, quantity);
                        cost = FinanceMath.costAfterSell(method, quantity, cost, sold, e.price, e.fees);
                        quantity = FinanceMath.sellQuantity(quantity, sold);
                    } else {
                        cost = FinanceMath.costAfterReceivedDividend(method, quantity, cost, e.receipt);
                    }
                } catch (IllegalArgumentException rejected) {
                    return null;
                }
            }
            if (!Double.isFinite(quantity) || !Double.isFinite(cost)) return null;
            return new Scenario(new CostReplay.State(openingQuantity, openingCost), events,
                    new CostReplay.State(Math.max(0d, quantity), cost));
        }
    }

    private static String plusDays(String from, Random random) {
        return java.time.LocalDate.parse(from).plusDays(1 + random.nextInt(120)).toString();
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) <= 1e-6 * Math.max(1d, Math.max(Math.abs(a), Math.abs(b)));
    }

    private static void check(String label, boolean actual, boolean expected) {
        assertions++;
        if (actual != expected) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        System.out.println("PASS  " + label);
    }
}
