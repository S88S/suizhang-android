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

        // weightedBuyCost 已标记 @Deprecated（无生产调用点）；此处保留调用是为了
        // 持续验证它与 costAfterBuy(COST_WEIGHTED_AVERAGE, ...) 算式一致，两者须长期同值。
        @SuppressWarnings("deprecation")
        double average = FinanceMath.weightedBuyCost(10, 10, 5, 14, 5);
        near("买入含手续费的加权均价", average, 175d / 15d);
        near("已废弃的 weightedBuyCost 与 costAfterBuy 加权口径同值",
                average, FinanceMath.costAfterBuy(FinanceMath.COST_WEIGHTED_AVERAGE, 10, 10, 5, 14, 5));
        near("卖出后的持仓量", FinanceMath.sellQuantity(15, 4), 11);
        near("卖出后均价不变时的剩余成本", FinanceMath.sellQuantity(15, 4) * average, 11 * (175d / 15d));
        near("加权平均卖出费计入剩余成本", FinanceMath.costAfterSell(FinanceMath.COST_WEIGHTED_AVERAGE, 10, 12, 4, 20, 2), 12.333333333333334);
        near("摊薄成本按净卖出回款扣减", FinanceMath.costAfterSell(FinanceMath.COST_DILUTED, 10, 12, 4, 20, 2), 7);
        near("分红摊薄卖出同按净卖出回款扣减", FinanceMath.costAfterSell(FinanceMath.COST_DIVIDEND_ADJUSTED, 10, 12, 4, 20, 2), 7);
        near("买入费用三种口径均计入成本", FinanceMath.costAfterBuy(FinanceMath.COST_DILUTED, 10, 12, 5, 14, 5), 13);
        near("分红摊薄只扣实际净到账", FinanceMath.costAfterReceivedDividend(FinanceMath.COST_DIVIDEND_ADJUSTED, 10, 12, 30), 9);
        near("其他成本口径不重复扣分红", FinanceMath.costAfterReceivedDividend(FinanceMath.COST_DILUTED, 10, 12, 30), 12);
        near("全数卖出后剩余成本为零", FinanceMath.costAfterSell(FinanceMath.COST_WEIGHTED_AVERAGE, 10, 12, 10, 20, 2), 0);
        near("年分红月均按 12 个月", FinanceMath.averageMonthlyDividend(1200), 100);
        near("年分红日均按 365 天", FinanceMath.averageDailyDividend(365), 1);
        near("成本息率", FinanceMath.costYield(120, 2000), .06);
        near("昨收息率", FinanceMath.marketYield(1.2, 20), .06);
        near("浮动盈亏", FinanceMath.floatingProfit(1200, 1000), 200);
        near("盈亏率", FinanceMath.profitRate(200, 1000), .20);
        near("同币种持仓占比", FinanceMath.positionWeight(250, 1000), .25);
        near("首买至今持股天数", FinanceMath.holdingDays(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-11")), 10);
        check("成本为零时盈亏率不可计算", Double.isNaN(FinanceMath.profitRate(10, 0)));
        check("无有效现价时昨收息率不可计算", Double.isNaN(FinanceMath.marketYield(1, 0)));
        expectFailure("超卖被阻止", () -> FinanceMath.sellQuantity(5, 6));

        near("日支出年化按 365 天", FinanceMath.annualExpense("日", 100), 36500);
        near("月支出年化按 12 个月", FinanceMath.annualExpense("月", 500), 6000);
        near("年支出不变", FinanceMath.annualExpense("年", 12000), 12000);
        near("覆盖率百分比", FinanceMath.coveragePercent(3000, 6000), 50);

        ArrayList<FinanceMath.Lot> oneMonth = new ArrayList<>(); oneMonth.add(new FinanceMath.Lot(100, LocalDate.parse("2025-07-01")));
        near("A股持有恰好一个月仍按不超过一个月情景", FinanceMath.aShareRecordDateTaxRate(oneMonth, LocalDate.parse("2025-08-01"), .20, .10, 0), .20);
        near("A股持有超过一个月一天转入一个月至一年情景", FinanceMath.aShareRecordDateTaxRate(oneMonth, LocalDate.parse("2025-08-02"), .20, .10, 0), .10);
        ArrayList<FinanceMath.Lot> oneYear = new ArrayList<>(); oneYear.add(new FinanceMath.Lot(100, LocalDate.parse("2024-08-01")));
        near("A股持有恰好一年仍按一年以内情景", FinanceMath.aShareRecordDateTaxRate(oneYear, LocalDate.parse("2025-08-01"), .20, .10, 0), .10);
        near("A股持有超过一年一天转入长期情景", FinanceMath.aShareRecordDateTaxRate(oneYear, LocalDate.parse("2025-08-02"), .20, .10, 0), 0);
        ArrayList<FinanceMath.Lot> longTerm = new ArrayList<>(); longTerm.add(new FinanceMath.Lot(100, LocalDate.parse("2024-07-31")));
        near("A股超过一年按长期情景", FinanceMath.aShareRecordDateTaxRate(longTerm, LocalDate.parse("2025-08-01"), .20, .10, 0), 0);
        ArrayList<FinanceMath.Lot> mixedLots = new ArrayList<>(); mixedLots.add(new FinanceMath.Lot(100, LocalDate.parse("2025-07-20"))); mixedLots.add(new FinanceMath.Lot(100, LocalDate.parse("2025-06-20")));
        near("A股 FIFO 批次按股份加权，不按证券统一固定税率", FinanceMath.aShareRecordDateTaxRate(mixedLots, LocalDate.parse("2025-08-01"), .20, .10, 0), .15);
        LocalDate coverageDate = LocalDate.parse("2025-08-01");
        ArrayList<FinanceMath.Lot> partialLots = new ArrayList<>(); partialLots.add(new FinanceMath.Lot(50, LocalDate.parse("2025-07-01")));
        check("未知期初 100 股加已知买入 50 股，只有 50 股有税批时拒绝覆盖", !FinanceMath.hasCompleteTaxLotCoverage(partialLots, 150, coverageDate));
        ArrayList<FinanceMath.Lot> knownAfterTrades = new ArrayList<>(); knownAfterTrades.add(new FinanceMath.Lot(60, LocalDate.parse("2024-06-01"))); knownAfterTrades.add(new FinanceMath.Lot(50, LocalDate.parse("2025-01-01")));
        check("有日期期初 100 股、买入 50 股、FIFO 卖出 40 股后 110 股全覆盖", FinanceMath.hasCompleteTaxLotCoverage(knownAfterTrades, 110, coverageDate));
        check("无任何日期批次时不把空批次误判为完整免税", !FinanceMath.hasCompleteTaxLotCoverage(new ArrayList<>(), 100, coverageDate));
        ArrayList<FinanceMath.Lot> lateLot = new ArrayList<>(); lateLot.add(new FinanceMath.Lot(100, LocalDate.parse("2025-08-02")));
        check("登记日之后才买入的批次不能覆盖登记日持仓", !FinanceMath.hasCompleteTaxLotCoverage(lateLot, 100, coverageDate));
        check("当前重放数量须与账面现量一致", FinanceMath.sameShareQuantity(100, 100) && !FinanceMath.sameShareQuantity(100, 99));

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
    private static void check(String name, boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError(name);
        System.out.println("PASS  " + name);
    }
    private static void expectFailure(String name, Runnable action) {
        assertions++;
        try { action.run(); } catch (IllegalArgumentException expected) { System.out.println("PASS  " + name); return; }
        throw new AssertionError(name + "：本应拒绝输入");
    }
}
