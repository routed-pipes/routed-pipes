package logisticspipes.application.computer;

// who runs a command: the id of the computer, and whether it sits right at the pipe it talks to
public record Caller(int computer, boolean direct) {

    // through another pipe, the pipe it talks to has no computer of its own to tell the id of
    public static final Caller REMOTE = new Caller(-1, false);

    public static Caller attached(int computer) {
        return new Caller(computer, true);
    }
}
