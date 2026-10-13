package routedpipes.domain.tube;

// a block a tube fills besides its main block
public record TubePart(Offset offset, PartType type) {

    static TubePart alone(int x, int y, int z) {
        return new TubePart(new Offset(x, y, z), PartType.NON_SHARE);
    }
}
