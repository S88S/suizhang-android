package cn.suizhang.ledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DividendReceivedAmountStateJUnitTest {

    @Test public void cancelingExistingReceivedRowKeepsStatusAndAmountForResave() {
        DividendReceivedAmountState state = new DividendReceivedAmountState(
                DividendReceivedAmountState.RECEIVED, "123.45");

        assertTrue("切换旧已到账记录到预计需要明确确认",
                state.requestStatus(DividendReceivedAmountState.EXPECTED));
        assertEquals("等待确认期间保留原到账状态", DividendReceivedAmountState.RECEIVED,
                state.statusForSave());
        assertEquals("等待确认期间保留原到账金额", "123.45", state.actualAmountForSave());

        state.cancelExpectedChange();
        assertEquals("取消后再次保存仍是已到账", DividendReceivedAmountState.RECEIVED,
                state.statusForSave());
        assertEquals("取消后再次保存仍带原实际到账金额", "123.45", state.actualAmountForSave());
    }

    @Test public void newEntryTypedReceivedAmountIsProtectedWhenSwitchingToExpected() {
        DividendReceivedAmountState state = new DividendReceivedAmountState(
                DividendReceivedAmountState.EXPECTED, "");
        assertFalse("新增记录先切换到已到账不需清空确认",
                state.requestStatus(DividendReceivedAmountState.RECEIVED));
        state.setActualAmount("18.20");

        assertTrue("新增记录填写到账额后切到预计需要确认",
                state.requestStatus(DividendReceivedAmountState.EXPECTED));
        state.cancelExpectedChange();
        assertEquals("取消后新增草稿恢复已到账", DividendReceivedAmountState.RECEIVED,
                state.statusForSave());
        assertEquals("取消后新增草稿金额不丢失", "18.20", state.actualAmountForSave());
    }

    @Test public void acceptingConfirmationIsTheOnlyTransitionThatClearsAmount() {
        DividendReceivedAmountState state = new DividendReceivedAmountState(
                DividendReceivedAmountState.RECEIVED, "42.75");
        assertTrue(state.requestStatus(DividendReceivedAmountState.EXPECTED));
        assertEquals("确认前金额仍在", "42.75", state.actualAmountForSave());

        state.confirmExpectedChange();
        assertEquals("明确确认后状态改为预计", DividendReceivedAmountState.EXPECTED,
                state.statusForSave());
        assertEquals("明确确认后才清空到账金额", "", state.actualAmountForSave());
    }

    @Test public void switchingWithoutAnAmountDoesNotAskForConfirmation() {
        DividendReceivedAmountState state = new DividendReceivedAmountState(
                DividendReceivedAmountState.RECEIVED, "  ");
        assertFalse(state.requestStatus(DividendReceivedAmountState.EXPECTED));
        assertEquals(DividendReceivedAmountState.EXPECTED, state.statusForSave());
        assertEquals("", state.actualAmountForSave());
    }
}
