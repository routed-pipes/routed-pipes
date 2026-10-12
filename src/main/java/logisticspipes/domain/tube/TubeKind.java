package logisticspipes.domain.tube;

import java.util.List;

import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.PipeTransport;

// the shapes are given for a tube facing north, with the main block at zero
public enum TubeKind {
    // one block, straight through
    LINE(1, List.of(), new TubeEnd(Offset.ZERO, Side.SOUTH), new TubeEnd(Offset.ZERO, Side.NORTH)),
    // the ramp between pipes and tubes, the main block is the end for pipes
    SPEEDUP(4, List.of(TubePart.alone(0, 0, -1), TubePart.alone(0, 0, -2), TubePart.alone(0, 0, -3)),
            new TubeEnd(Offset.ZERO, Side.SOUTH), new TubeEnd(new Offset(0, 0, -3), Side.NORTH)),
    // a quarter circle from the north side of the main block round to the west
    CURVE(Math.PI / 2 * TubeKind.CURVE_RADIUS, List.of(
            new TubePart(new Offset(-1, 0, 0), PartType.CURVE_INNER_A), new TubePart(new Offset(0, 0, 1), PartType.CURVE_OUT_A),
            TubePart.alone(-1, 0, 1), new TubePart(new Offset(-2, 0, 1), PartType.CURVE_INNER_B),
            new TubePart(new Offset(-1, 0, 2), PartType.CURVE_OUT_B), TubePart.alone(-2, 0, 2)),
            new TubeEnd(Offset.ZERO, Side.NORTH), new TubeEnd(new Offset(-2, 0, 2), Side.WEST)),
    // four blocks north and one east on the way
    S_CURVE(4, List.of(
            new TubePart(new Offset(0, 0, -1), PartType.S_CURVE_B), new TubePart(new Offset(0, 0, -2), PartType.S_CURVE_A),
            new TubePart(new Offset(1, 0, -1), PartType.S_CURVE_A), new TubePart(new Offset(1, 0, -2), PartType.S_CURVE_B),
            TubePart.alone(1, 0, -3)),
            new TubeEnd(Offset.ZERO, Side.SOUTH), new TubeEnd(new Offset(1, 0, -3), Side.NORTH)),
    // four blocks north and one up on the way
    GAIN(4, List.of(
            new TubePart(new Offset(0, 0, -1), PartType.GAIN_B), new TubePart(new Offset(0, 0, -2), PartType.GAIN_A),
            new TubePart(new Offset(0, 1, -1), PartType.GAIN_A), new TubePart(new Offset(0, 1, -2), PartType.GAIN_B),
            TubePart.alone(0, 1, -3)),
            new TubeEnd(Offset.ZERO, Side.SOUTH), new TubeEnd(new Offset(0, 1, -3), Side.NORTH));

    // a pipe counts this much towards the length of a route, a tube an eighth of that per block
    public static final int PIPE_WEIGHT = 8;
    // of the middle of a curve tube, around the north west corner of the block two west of the main block
    private static final double CURVE_RADIUS = 2.5;

    private final int length;
    private final List<TubePart> parts;
    private final List<TubeEnd> ends;

    TubeKind(double blocks, List<TubePart> parts, TubeEnd first, TubeEnd second) {
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

    // true when the far end points sideways
    boolean bends() {
        return this == CURVE;
    }

    // true when the far end is one block to the side of the near one
    boolean shifts() {
        return this == S_CURVE;
    }

    // true when it matters to a player which of the two ends is the near one
    boolean oneWay() {
        return this != CURVE && this != S_CURVE;
    }

    // the blocks it fills besides the main block
    List<TubePart> parts() {
        return parts;
    }

    List<TubeEnd> ends() {
        return ends;
    }

    // how many straight pieces stand in for the path when working out the room it takes
    int pieces() {
        return this == LINE || this == SPEEDUP ? 1 : 48;
    }

    // where an item is after the given part of the way from the first end to the second
    Point pointAt(double fraction) {
        return switch (this) {
            case LINE -> new Point(0.5, 0.5, 1 - fraction);
            case SPEEDUP -> new Point(0.5, 0.5, 1 - 4 * fraction);
            case CURVE -> new Point(-2 + CURVE_RADIUS * Math.cos(fraction * Math.PI / 2), 0.5,
                    CURVE_RADIUS * Math.sin(fraction * Math.PI / 2));
            case S_CURVE -> new Point(0.5 + eased(fraction), 0.5, 1 - 4 * fraction);
            case GAIN -> new Point(0.5, 0.5 + eased(fraction), 1 - 4 * fraction);
        };
    }

    // from 0 to 1 without a kink at either end, the way the tubes that move over by a block are bent
    private static double eased(double fraction) {
        return fraction * fraction * fraction * (fraction * (6 * fraction - 15) + 10);
    }
}
