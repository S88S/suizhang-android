package cn.suizhang.ledger;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Deterministic offline calculations; all rates are decimals (e.g. 0.10 for 10%). */
final class FinanceMath {
    static final String COST_DIVIDEND_ADJUSTED = "dividend_adjusted";
    static final String COST_DILUTED = "diluted";
    static final String COST_WEIGHTED_AVERAGE = "weighted_average";

    private FinanceMath() {}

    static double weightedBuyCost(double oldQuantity, double oldUnitCost,
                                  double buyQuantity, double buyPrice, double fees) {
        if (oldQuantity < 0 || oldUnitCost < 0 || buyQuantity <= 0 || buyPrice < 0 || fees < 0) {
            throw new IllegalArgumentException("数量、成本、价格或手续费不合法");
        }
        return (oldQuantity * oldUnitCost + buyQuantity * buyPrice + fees) / (oldQuantity + buyQuantity);
    }

    /** All supported cost bases include buy fees in the amount invested. */
    static double costAfterBuy(String method, double oldQuantity, double oldUnitCost,
                               double buyQuantity, double buyPrice, double fees) {
        requireCostMethod(method);
        if (oldQuantity < 0 || !Double.isFinite(oldUnitCost) || buyQuantity <= 0 ||
                !Double.isFinite(buyQuantity) || buyPrice < 0 || !Double.isFinite(buyPrice) ||
                fees < 0 || !Double.isFinite(fees)) throw new IllegalArgumentException("买入参数不合法");
        double total = oldQuantity * oldUnitCost + buyQuantity * buyPrice + fees;
        return total / (oldQuantity + buyQuantity);
    }

    /**
     * Returns the remaining per-unit cost after a sale. Weighted average removes units at the
     * current average cost; the two dilution methods reduce the basis by net sale proceeds.
     * A sale fee increases the remaining basis. Fully closed positions have no remaining basis.
     */
    static double costAfterSell(String method, double oldQuantity, double oldUnitCost,
                                double sellQuantity, double sellPrice, double fees) {
        requireCostMethod(method);
        if (oldQuantity < 0 || !Double.isFinite(oldUnitCost) || sellQuantity <= 0 ||
                !Double.isFinite(sellQuantity) || sellPrice < 0 || !Double.isFinite(sellPrice) ||
                fees < 0 || !Double.isFinite(fees)) throw new IllegalArgumentException("卖出参数不合法");
        double remaining = sellQuantity(oldQuantity, sellQuantity);
        if (remaining <= 1e-8) return 0d;
        double remainingBasis;
        if (COST_WEIGHTED_AVERAGE.equals(method)) {
            remainingBasis = oldUnitCost * remaining + fees;
        } else {
            remainingBasis = oldUnitCost * oldQuantity - sellQuantity * sellPrice + fees;
        }
        return remainingBasis / remaining;
    }

    /** Deducts an explicitly entered net cash receipt only for dividend-adjusted cost. */
    static double costAfterReceivedDividend(String method, double quantity, double unitCost,
                                            double actualNetReceipt) {
        requireCostMethod(method);
        if (quantity < 0 || !Double.isFinite(quantity) || !Double.isFinite(unitCost) ||
                actualNetReceipt < 0 || !Double.isFinite(actualNetReceipt)) {
            throw new IllegalArgumentException("到账金额或持仓成本不合法");
        }
        if (!COST_DIVIDEND_ADJUSTED.equals(method) || quantity <= 0d) return unitCost;
        return (quantity * unitCost - actualNetReceipt) / quantity;
    }

    private static void requireCostMethod(String method) {
        if (!COST_DIVIDEND_ADJUSTED.equals(method) && !COST_DILUTED.equals(method) &&
                !COST_WEIGHTED_AVERAGE.equals(method)) throw new IllegalArgumentException("不支持的成本算法");
    }

    static double sellQuantity(double oldQuantity, double sellQuantity) {
        if (oldQuantity < 0 || sellQuantity <= 0 || sellQuantity > oldQuantity + 1e-8) {
            throw new IllegalArgumentException("卖出数量超过持仓或不合法");
        }
        return Math.max(0d, oldQuantity - sellQuantity);
    }

    static double afterTax(double gross, double taxRate) {
        if (gross < 0 || taxRate < 0 || taxRate > 1) throw new IllegalArgumentException("金额或税率不合法");
        return gross * (1d - taxRate);
    }

    static double annualDividend(double quantity, double annualPerUnit) {
        if (quantity < 0 || annualPerUnit < 0) throw new IllegalArgumentException("数量或年派息金额不合法");
        return quantity * annualPerUnit;
    }

    static double averageMonthlyDividend(double annualTotal) {
        if (annualTotal < 0 || !Double.isFinite(annualTotal)) throw new IllegalArgumentException("年度分红金额不合法");
        return annualTotal / 12d;
    }

    static double averageDailyDividend(double annualTotal) {
        if (annualTotal < 0 || !Double.isFinite(annualTotal)) throw new IllegalArgumentException("年度分红金额不合法");
        return annualTotal / 365d;
    }

    static double costYield(double annualDividend, double totalCost) {
        return annualDividend < 0 || totalCost <= 0 || !Double.isFinite(annualDividend) || !Double.isFinite(totalCost)
                ? Double.NaN : annualDividend / totalCost;
    }

    static double marketYield(double annualDividend, double latestPrice) {
        return annualDividend < 0 || latestPrice <= 0 || !Double.isFinite(annualDividend) || !Double.isFinite(latestPrice)
                ? Double.NaN : annualDividend / latestPrice;
    }

    static double floatingProfit(double marketValue, double totalCost) {
        if (!Double.isFinite(marketValue) || !Double.isFinite(totalCost)) return Double.NaN;
        return marketValue - totalCost;
    }

    static double profitRate(double floatingProfit, double totalCost) {
        return !Double.isFinite(floatingProfit) || !Double.isFinite(totalCost) || totalCost <= 0
                ? Double.NaN : floatingProfit / totalCost;
    }

    static double positionWeight(double positionMarketValue, double sameCurrencyPortfolioMarketValue) {
        return !Double.isFinite(positionMarketValue) || !Double.isFinite(sameCurrencyPortfolioMarketValue) ||
                positionMarketValue < 0 || sameCurrencyPortfolioMarketValue <= 0
                ? Double.NaN : positionMarketValue / sameCurrencyPortfolioMarketValue;
    }

    static long holdingDays(LocalDate firstBuyDate, LocalDate today) {
        if (firstBuyDate == null || today == null || firstBuyDate.isAfter(today)) return -1;
        return ChronoUnit.DAYS.between(firstBuyDate, today);
    }

    static final class Lot {
        final double quantity;
        final LocalDate acquiredOn;
        Lot(double quantity, LocalDate acquiredOn) { this.quantity = quantity; this.acquiredOn = acquiredOn; }
    }

    /** Weighted A-share dividend tax scenario by holding duration on the record date (not a tax filing result). */
    static double aShareRecordDateTaxRate(List<Lot> lots, LocalDate recordDate,
                                          double shortRate, double mediumRate, double longRate) {
        if (recordDate == null || shortRate < 0 || shortRate > 1 || mediumRate < 0 || mediumRate > 1 || longRate < 0 || longRate > 1)
            throw new IllegalArgumentException("登记日或税率不合法");
        double shares = 0, taxWeightedShares = 0;
        for (Lot lot : lots) {
            if (lot == null || lot.quantity <= 0 || lot.acquiredOn == null || lot.acquiredOn.isAfter(recordDate)) continue;
            double rate = !recordDate.isAfter(lot.acquiredOn.plusMonths(1)) ? shortRate
                    : !recordDate.isAfter(lot.acquiredOn.plusYears(1)) ? mediumRate : longRate;
            shares += lot.quantity; taxWeightedShares += lot.quantity * rate;
        }
        return shares == 0 ? 0 : taxWeightedShares / shares;
    }

    /** True only when dated, positive lots account for every share held on the same date. */
    static boolean hasCompleteTaxLotCoverage(List<Lot> lots, double expectedQuantity, LocalDate asOf) {
        if (lots == null || lots.isEmpty() || asOf == null || expectedQuantity <= 0 ||
                !Double.isFinite(expectedQuantity)) return false;
        double covered = 0d;
        for (Lot lot : lots) {
            if (lot == null || lot.quantity <= 0d || !Double.isFinite(lot.quantity) ||
                    lot.acquiredOn == null || lot.acquiredOn.isAfter(asOf)) return false;
            covered += lot.quantity;
        }
        return sameShareQuantity(covered, expectedQuantity);
    }

    static boolean sameShareQuantity(double left, double right) {
        if (left < 0d || right < 0d || !Double.isFinite(left) || !Double.isFinite(right)) return false;
        double tolerance = Math.max(1e-8, Math.max(Math.abs(left), Math.abs(right)) * 1e-12);
        return Math.abs(left - right) <= tolerance;
    }

    static double annualExpense(String period, double amount) {
        if (amount < 0) throw new IllegalArgumentException("支出金额不能为负数");
        switch (period) {
            case "日": return amount * 365d;
            case "月": return amount * 12d;
            case "年": return amount;
            default: throw new IllegalArgumentException("不支持的支出周期");
        }
    }

    static double coveragePercent(double annualIncome, double annualExpense) {
        if (annualExpense <= 0) return 0;
        return annualIncome / annualExpense * 100d;
    }

    static List<DripYear> project(double startingAssets, double annualYield, double annualContribution,
                                  double reinvestPercent, double years, double taxRate) {
        if (startingAssets < 0 || annualYield < 0 || annualContribution < 0 ||
                reinvestPercent < 0 || reinvestPercent > 1 || years < 1 || years > 30 ||
                taxRate < 0 || taxRate > 1) throw new IllegalArgumentException("复利参数超出有效范围");
        ArrayList<DripYear> rows = new ArrayList<>();
        double opening = startingAssets, cumulativeGross = 0, cumulativeNet = 0;
        for (int i = 1; i <= (int) years; i++) {
            double gross = opening * annualYield;
            double net = afterTax(gross, taxRate);
            double reinvested = net * reinvestPercent;
            double closing = opening + reinvested + annualContribution;
            cumulativeGross += gross;
            cumulativeNet += net;
            rows.add(new DripYear(i, opening, gross, net, reinvested,
                    annualContribution, closing, cumulativeGross, cumulativeNet));
            opening = closing;
        }
        return rows;
    }

    static final class DripYear {
        final int year;
        final double opening, grossDividend, netDividend, reinvested, contribution,
                closing, cumulativeGross, cumulativeNet;
        DripYear(int year, double opening, double grossDividend, double netDividend,
                 double reinvested, double contribution, double closing,
                 double cumulativeGross, double cumulativeNet) {
            this.year = year; this.opening = opening; this.grossDividend = grossDividend;
            this.netDividend = netDividend; this.reinvested = reinvested;
            this.contribution = contribution; this.closing = closing;
            this.cumulativeGross = cumulativeGross; this.cumulativeNet = cumulativeNet;
        }
    }
}
