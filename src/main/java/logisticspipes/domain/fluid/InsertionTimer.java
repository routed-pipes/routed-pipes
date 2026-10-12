package logisticspipes.domain.fluid;

// when one side of an insertion pipe sends again: a full tank soon, a partly filled one waits for more to come
public final class InsertionTimer {

    public static final int WAIT_FOR_MORE = 100;
    public static final int AFTER_SENDING = 5;
    public static final int AFTER_FAILING = 10;

    private int waitForMore;
    private int pause;

    public InsertionTimer() {
    }

    public InsertionTimer(int waitForMore, int pause) {
        this.waitForMore = waitForMore;
        this.pause = pause;
    }

    // asked every tick while the tank holds fluid, true when it is time to send
    public boolean due(boolean full) {
        if (waitForMore > 0 && !full) {
            waitForMore--;
            return false;
        }
        if (pause > 0) {
            pause--;
            return false;
        }
        return true;
    }

    public void sent() {
        waitForMore = WAIT_FOR_MORE;
        pause = AFTER_SENDING;
    }

    public void failed() {
        waitForMore = WAIT_FOR_MORE;
        pause = AFTER_FAILING;
    }

    public int waitForMore() {
        return waitForMore;
    }

    public int pause() {
        return pause;
    }
}
