package cn.suizhang.ledger;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

/** Deterministic offline calculations; all rates are decimals (e.g. 0.10 for 10%). */
final class FinanceMath {
    private FinanceMath() {}

    static double weightedBuyCost(double oldQuantity, double oldUnitCost,
                                  double buyQuantity, double buyPrice, double fees) {
        if (oldQuantity < 0 || oldUnitCost < 0 || buyQuantity <= 0 || buyPrice < 0 || fees < 0) {
            throw new IllegalArgumentException("数量、成本、价格或手续费不合法");
        }
        return (oldQuantity * oldUnitCost + buyQuantity * buyPrice + fees) / (oldQuantity + buyQuantity);
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
