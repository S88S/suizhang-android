package cn.suizhang.ledger;

/** Keeps a dividend editor's received status and actual amount together during status changes. */
final class DividendReceivedAmountState {
    static final String EXPECTED = "expected";
    static final String RECEIVED = "received";

    private String status;
    private String actualAmount;
    private boolean expectedChangePending;

    DividendReceivedAmountState(String initialStatus, String initialAmount) {
        status = RECEIVED.equals(initialStatus) ? RECEIVED : EXPECTED;
        actualAmount = initialAmount == null ? "" : initialAmount;
    }

    /** Returns true when changing to expected would discard a non-empty actual amount. */
    boolean requestStatus(String requestedStatus) {
        if (!RECEIVED.equals(requestedStatus) && !EXPECTED.equals(requestedStatus)) {
            throw new IllegalArgumentException("Unknown dividend status: " + requestedStatus);
        }
        if (requestedStatus.equals(status)) return false;
        if (EXPECTED.equals(requestedStatus) && RECEIVED.equals(status)
                && !actualAmount.trim().isEmpty()) {
            expectedChangePending = true;
            return true;
        }
        status = requestedStatus;
        expectedChangePending = false;
        return false;
    }

    void setActualAmount(String value) {
        actualAmount = value == null ? "" : value;
    }

    void cancelExpectedChange() {
        expectedChangePending = false;
    }

    /** Applies the pending destructive change only after the user confirms it. */
    void confirmExpectedChange() {
        if (!expectedChangePending) return;
        status = EXPECTED;
        actualAmount = "";
        expectedChangePending = false;
    }

    String statusForSave() {
        return status;
    }

    String actualAmountForSave() {
        return actualAmount.trim();
    }
}
