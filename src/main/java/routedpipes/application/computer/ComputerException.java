package routedpipes.application.computer;

// ends a command, the script gets the message as its error
public final class ComputerException extends RuntimeException {

    public ComputerException(String message) {
        super(message);
    }
}
