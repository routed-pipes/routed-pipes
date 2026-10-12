package logisticspipes.domain.tube;

import static org.assertj.core.api.Assertions.assertThat;

import logisticspipes.domain.routing.Side;
import org.junit.jupiter.api.Test;

class TubePlacementTest {

    @Test
    void aStraightTubeRunsAwayFromThePlayer() {
        assertThat(TubePlacement.of(TubeKind.SPEEDUP, 2, 0.3)).isEqualTo(placed(TubeKind.SPEEDUP, Side.EAST));
        assertThat(TubePlacement.of(TubeKind.SPEEDUP, -2, 0.3)).isEqualTo(placed(TubeKind.SPEEDUP, Side.WEST));
        assertThat(TubePlacement.of(TubeKind.SPEEDUP, 0.3, 2)).isEqualTo(placed(TubeKind.SPEEDUP, Side.SOUTH));
        assertThat(TubePlacement.of(TubeKind.SPEEDUP, 0.3, -2)).isEqualTo(placed(TubeKind.SPEEDUP, Side.NORTH));
        assertThat(TubePlacement.of(TubeKind.LINE, -0.3, -2)).isEqualTo(placed(TubeKind.LINE, Side.NORTH));
    }

    @Test
    void theClickedBlockIsTheEndForPipes() {
        TubePlacement placement = TubePlacement.of(TubeKind.SPEEDUP, 2, 0);

        assertThat(placement.main()).isEqualTo(Offset.ZERO);
        assertThat(placement.shape().end(0)).isEqualTo(new TubeEnd(Offset.ZERO, Side.WEST));
    }

    @Test
    void aBlockStraightBelowThePlayerStillGetsATube() {
        assertThat(TubePlacement.of(TubeKind.LINE, 0, 0).shape().kind()).isEqualTo(TubeKind.LINE);
    }

    private static TubePlacement placed(TubeKind kind, Side facing) {
        return new TubePlacement(new TubeShape(kind, facing), Offset.ZERO);
    }
}
