package cn.suizhang.ledger;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Window rules for the daily dividend reminder. The boundaries are the usual off-by-one spot:
 * a payout today must fire, one seven days out must fire, one eight days out must not, and one
 * that already happened must not.
 *
 * <p>Only plain strings and LocalDate cross into this class. Unit tests run against the
 * android.jar stubs, where JSONObject returns null from every accessor, so passing a row in
 * here would make every case untestable.
 */
public class DividendMilestonesJUnitTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    @Test public void windowIncludesToday() {
        assertTrue(DividendMilestones.withinWindow("2026-10-10", TODAY));
    }

    @Test public void windowIncludesTheLastDay() {
        assertTrue(DividendMilestones.withinWindow("2026-10-17", TODAY));
    }

    @Test public void windowExcludesTheDayAfterTheLastDay() {
        assertFalse(DividendMilestones.withinWindow("2026-10-18", TODAY));
    }

    @Test public void windowExcludesYesterday() {
        assertFalse(DividendMilestones.withinWindow("2026-10-09", TODAY));
    }

    @Test public void windowRejectsUnparseableAndEmptyDates() {
        assertFalse(DividendMilestones.withinWindow("2026-02-30", TODAY));
        assertFalse(DividendMilestones.withinWindow("", TODAY));
        assertFalse(DividendMilestones.withinWindow(null, TODAY));
        assertFalse(DividendMilestones.withinWindow("2026-10-12", null));
    }

    @Test public void windowToleratesSurroundingSpace() {
        assertTrue(DividendMilestones.withinWindow(" 2026-10-12 ", TODAY));
    }

    @Test public void payDateWinsOverExAndRecordDates() {
        assertEquals("预计 2026-10-12 派息",
                DividendMilestones.milestoneOf("2026-10-11", "2026-10-12", "2026-10-12", TODAY));
    }

    @Test public void exDateIsUsedWhenPayDateIsOutsideTheWindow() {
        assertEquals("预计 2026-10-11 除息",
                DividendMilestones.milestoneOf("2026-10-11", "2026-10-11", "2026-12-01", TODAY));
    }

    @Test public void recordDateIsUsedWhenPayAndExAreOutsideTheWindow() {
        assertEquals("预计 2026-10-13 登记日",
                DividendMilestones.milestoneOf("2026-10-13", "2026-11-01", "2026-12-01", TODAY));
    }

    @Test public void noMilestoneWhenEveryDateIsOutsideTheWindow() {
        assertNull(DividendMilestones.milestoneOf("2026-09-01", "2026-09-02", "2026-09-03", TODAY));
    }

    @Test public void noMilestoneForMissingOrNullDates() {
        assertNull(DividendMilestones.milestoneOf("", "", "", TODAY));
        assertNull(DividendMilestones.milestoneOf(null, null, null, TODAY));
        assertNull(DividendMilestones.milestoneOf("2026-10-11", "", "", null));
    }

    @Test public void lookaheadIsSevenDays() {
        assertEquals(7, DividendMilestones.LOOKAHEAD_DAYS);
    }
}
