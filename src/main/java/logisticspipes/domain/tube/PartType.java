package logisticspipes.domain.tube;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// what of a tube runs through one of its blocks, two tubes can share a block when their parts leave each other room
public enum PartType {
    NON_SHARE, CURVE_INNER_A, CURVE_OUT_A, CURVE_INNER_B, CURVE_OUT_B, S_CURVE_A, S_CURVE_B, GAIN_A, GAIN_B;

    private static final Set<Set<PartType>> PAIRS = Set.of(
            EnumSet.of(CURVE_OUT_A, CURVE_INNER_A), EnumSet.of(CURVE_OUT_B, CURVE_INNER_B),
            EnumSet.of(S_CURVE_A, S_CURVE_B), EnumSet.of(S_CURVE_A), EnumSet.of(CURVE_OUT_A, S_CURVE_A),
            EnumSet.of(CURVE_OUT_B, S_CURVE_A), EnumSet.of(GAIN_A, GAIN_B), EnumSet.of(GAIN_A));

    // whether a block that holds parts of these types already takes one more
    public static boolean canShare(List<PartType> present, PartType added) {
        if (present.isEmpty()) {
            return true;
        }
        return present.size() == 1 && PAIRS.contains(EnumSet.of(present.get(0), added));
    }
}
