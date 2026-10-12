package logisticspipes.domain.tube;

import java.util.List;

import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.PipeTransport;

// the shapes are given for a tube facing north, with the main block at zero
public enum TubeKind {
    // one block, straight through
    LINE(1, List.of(), new TubeEnd(Offset.ZERO, Side.SOUTH), new TubeEnd(Offset.ZERO, Side.NORTH)),
    // the ramp between pipes and tubes, the main block is the end for pipes
    SPEEDUP(4, List.of(new Offset(0, 0, -1), new Offset(0, 0, -2), new Offset(0, 0, -3)),
            new TubeEnd(Offset.ZERO, Side.SOUTH), new TubeEnd(new Offset(0, 0, -3), Side.NORTH));

    // a pipe counts this much towards the length of a route, a tube an eighth of that per block
    public static final int PIPE_WEIGHT = 8;

    private final int length;
    private final List<Offset> parts;
    private final List<TubeEnd> ends;

    TubeKind(double blocks, List<Offset> parts, TubeEnd first, TubeEnd second) {
        this.length = (int) Math.round(blocks * PipeTransport.PIPE_LENGTH);
        this.parts = parts;
        this.ends = List.of(first, second);
    }

    // steps from one end to the other, see PipeTransport.PIPE_LENGTH
    public int length() {
        return length;
    }

    public int weight() {
        return Math.round(length / (float) PipeTransport.PIPE_LENGTH);
    }

    // only this end connects to pipes, all others to tubes
    public boolean pipeEnd(int end) {
        return this == SPEEDUP && end == 0;
    }

    // the blocks it fills besides the main block
    List<Offset> parts() {
        return parts;
    }

    List<TubeEnd> ends() {
        return ends;
    }

    // how many straight pieces stand in for the path when working out the room it takes
    int pieces() {
        return 1;
    }

    // where an item is after the given part of the way from the first end to the second
    Point pointAt(double fraction) {
        return switch (this) {
            case LINE -> new Point(0.5, 0.5, 1 - fraction);
            case SPEEDUP -> new Point(0.5, 0.5, 1 - 4 * fraction);
        };
    }
}
