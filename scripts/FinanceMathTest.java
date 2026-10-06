package cn.suizhang.ledger;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDate;

public final class FinanceMathTest {
    private static int assertions;
    public static void main(String[] args) {
        near("税前金额保持原值", FinanceMath.afterTax(100, 0), 100);
        near("20% 税率税后分红", FinanceMath.afterTax(100, .20), 80);
        near("10% 税率税后分红", FinanceMath.afterTax(250, .10), 225);
        near("持仓年分红", FinanceMath.annualDividend(80, 2.5), 200);

        double average = FinanceMath.weightedBuyCost(10, 10, 5, 14, 5);
        near("买入含手续费的加权均价", average, 175d / 15d);
        near("卖出后的持仓量", FinanceMath.sellQuantity(15, 4), 11);
        near("卖出后均价不变时的剩余成本", FinanceMath.sellQuantity(15, 4) * average, 11 * (175d / 15d));
        expectFailure("超卖被阻止", () -> FinanceMath.sellQuantity(5, 6));

        near("日支出年化按 365 天", FinanceMath.annualExpense("日", 100), 36500);
        near("月支出年化按 12 个月", FinanceMath.annualExpense("月", 500), 6000);
        near("年支出不变", FinanceMath.annualExpense("年", 12000), 12000);
        near("覆盖率百分比", FinanceMath.coveragePercent(3000, 6000), 50);

        ArrayList<FinanceMath.Lot> oneMonth = new ArrayList<>(); oneMonth.add(new FinanceMath.Lot(100, LocalDate.parse("2025-07-01")));
        near("A股持有恰好一个月仍按不超过一个月情景", FinanceMath.aShareRecordDateTaxRate(oneMonth, LocalDate.parse("2025-08-01"), .20, .10, 0), .20);
        ArrayList<FinanceMath.Lot> oneYear = new ArrayList<>(); oneYear.add(new FinanceMath.Lot(100, LocalDate.parse("2024-08-01")));
        near("A股持有恰好一年仍按一年以内情景", FinanceMath.aShareRecordDateTaxRate(oneYear, LocalDate.parse("2025-08-01"), .20, .10, 0), .10);
        ArrayList<FinanceMath.Lot> longTerm = new ArrayList<>(); longTerm.add(new FinanceMath.Lot(100, LocalDate.parse("2024-07-31")));
        near("A股超过一年按长期情景", FinanceMath.aShareRecordDateTaxRate(longTerm, LocalDate.parse("2025-08-01"), .20, .10, 0), 0);
        ArrayList<FinanceMath.Lot> mixedLots = new ArrayList<>(); mixedLots.add(new FinanceMath.Lot(100, LocalDate.parse("2025-07-20"))); mixedLots.add(new FinanceMath.Lot(100, LocalDate.parse("2025-06-20")));
        near("A股 FIFO 批次按股份加权，不按证券统一固定税率", FinanceMath.aShareRecordDateTaxRate(mixedLots, LocalDate.parse("2025-08-01"), .20, .10, 0), .15);

        List<FinanceMath.DripYear> drip = FinanceMath.project(1000, .10, 100, .50, 2, .20);
        near("DRIP 第 1 年税前分红", drip.get(0).grossDividend, 100);
        near("DRIP 第 1 年税后分红", drip.get(0).netDividend, 80);
        near("DRIP 第 1 年再投资", drip.get(0).reinvested, 40);
        near("DRIP 第 1 年期末资产", drip.get(0).closing, 1140);
        near("DRIP 第 2 年期初资产迭代", drip.get(1).opening, 1140);
        near("DRIP 第 2 年税前分红", drip.get(1).grossDividend, 114);
        near("DRIP 第 2 年税后分红", drip.get(1).netDividend, 91.2);
        near("DRIP 第 2 年期末资产", drip.get(1).closing, 1285.6);
        near("DRIP 累计税前分红", drip.get(1).cumulativeGross, 214);
        near("DRIP 累计税后分红", drip.get(1).cumulativeNet, 171.2);
        List<FinanceMath.DripYear> noReinvest = FinanceMath.project(500, .10, 100, 0, 2, .20);
        near("再投资比例为零时不把股息加入资产", noReinvest.get(0).closing, 600);
        near("零再投资时仅年度定投推动期初资产", noReinvest.get(1).opening, 600);
        near("零再投资时年度投入继续计入期末", noReinvest.get(1).closing, 700);
        System.out.println("通过：" + assertions + " 项核心计算断言");
    }
    private static void near(String name, double actual, double expected) {
        assertions++;
        if (Math.abs(actual - expected) > 1e-8) throw new AssertionError(name + "：实际=" + actual + "，预期=" + expected);
        System.out.println("PASS  " + name + " = " + actual);
    }
    private static void expectFailure(String name, Runnable action) {
        assertions++;
        try { action.run(); } catch (IllegalArgumentException expected) { System.out.println("PASS  " + name); return; }
        throw new AssertionError(name + "：本应拒绝输入");
    }
}
