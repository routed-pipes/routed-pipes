package logisticspipes.domain.program;

// what a programmer has to be flashed with to craft one thing, the id is the one of the item that is crafted
public record Program(Kind kind, String id) {

    // in the order the compiler lists them
    public enum Kind {
        PIPE, MODULE, UPGRADE
    }
}
