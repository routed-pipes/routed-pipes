package routedpipes.domain.tube;

import java.util.Optional;

public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    // the smallest box around both points, grown by the margin on every side
    static Box around(Point first, Point second, double margin) {
        return new Box(Math.min(first.x(), second.x()) - margin, Math.min(first.y(), second.y()) - margin,
                Math.min(first.z(), second.z()) - margin, Math.max(first.x(), second.x()) + margin,
                Math.max(first.y(), second.y()) + margin, Math.max(first.z(), second.z()) + margin);
    }

    // what of this box lies inside the block, counted from the corner of that block
    Optional<Box> within(Offset block) {
        Box cut = new Box(Math.max(minX - block.x(), 0), Math.max(minY - block.y(), 0), Math.max(minZ - block.z(), 0),
                Math.min(maxX - block.x(), 1), Math.min(maxY - block.y(), 1), Math.min(maxZ - block.z(), 1));
        return cut.maxX > cut.minX && cut.maxY > cut.minY && cut.maxZ > cut.minZ ? Optional.of(cut) : Optional.empty();
    }
}
