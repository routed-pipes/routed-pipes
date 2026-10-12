package logisticspipes.domain.tube;

import static org.assertj.core.api.Assertions.assertThat;

import logisticspipes.domain.routing.Side;
import org.junit.jupiter.api.Test;

class TubePlacementTest {

    // a player standing on the given side of the clicked block, looking mostly at it and a bit towards another side
    private static TubePlacement place(TubeKind kind, Side from, Side looking) {
        Offset towards = Offset.of(from.opposite());
        Offset aside = Offset.of(looking);
        return TubePlacement.of(kind, towards.x() * 2, towards.z() * 2, towards.x() + aside.x() * 0.2, towards.z() + aside.z() * 0.2);
    }

    private static TubePlacement placed(TubeKind kind, Side facing, int x, int z) {
        return new TubePlacement(new TubeShape(kind, facing), new Offset(x, 0, z));
    }

    private static TubePlacement mirrored(Side facing, int x, int z) {
        return new TubePlacement(new TubeShape(TubeKind.S_CURVE, facing, true), new Offset(x, 0, z));
    }

    // the eight ways of the original, by the way the tube leads and the side the player looks to
    @Test
    void anSCurveLeadsAwayFromThePlayerAndMovesOverToTheSideTheyLookAt() {
        assertThat(place(TubeKind.S_CURVE, Side.SOUTH, Side.EAST)).isEqualTo(placed(TubeKind.S_CURVE, Side.NORTH, 0, 0));
        assertThat(place(TubeKind.S_CURVE, Side.SOUTH, Side.WEST)).isEqualTo(mirrored(Side.NORTH, 0, 0));
        assertThat(place(TubeKind.S_CURVE, Side.WEST, Side.SOUTH)).isEqualTo(placed(TubeKind.S_CURVE, Side.EAST, 0, 0));
        assertThat(place(TubeKind.S_CURVE, Side.WEST, Side.NORTH)).isEqualTo(mirrored(Side.EAST, 0, 0));
        assertThat(place(TubeKind.S_CURVE, Side.NORTH, Side.WEST)).isEqualTo(placed(TubeKind.S_CURVE, Side.NORTH, -1, 3));
        assertThat(place(TubeKind.S_CURVE, Side.NORTH, Side.EAST)).isEqualTo(mirrored(Side.NORTH, 1, 3));
        assertThat(place(TubeKind.S_CURVE, Side.EAST, Side.NORTH)).isEqualTo(placed(TubeKind.S_CURVE, Side.EAST, -3, -1));
        assertThat(place(TubeKind.S_CURVE, Side.EAST, Side.SOUTH)).isEqualTo(mirrored(Side.EAST, -3, 1));
    }

    @Test
    void theFarEndOfAnSCurveIsOneBlockOverToTheSideThePlayerLooksAt() {
        for (Side from : new Side[] {Side.NORTH, Side.EAST, Side.SOUTH, Side.WEST}) {
            for (Side looking : new Side[] {Side.NORTH, Side.EAST, Side.SOUTH, Side.WEST}) {
                if (looking == from || looking == from.opposite()) {
                    continue;
                }
                TubePlacement placement = place(TubeKind.S_CURVE, from, looking);
                TubeShape shape = placement.shape();
                Offset clicked = Offset.ZERO.minus(placement.main());

                int near = shape.endAt(clicked, from).orElseThrow();
                TubeEnd far = shape.end(1 - near);
                assertThat(far.side()).isEqualTo(from.opposite());
                assertThat(far.block().minus(clicked))
                        .isEqualTo(Offset.of(looking).plus(Offset.of(from.opposite()).plus(Offset.of(from.opposite()))
                                .plus(Offset.of(from.opposite()))));
            }
        }
    }

    @Test
    void aGainTubeClimbsAwayFromThePlayer() {
        assertThat(place(TubeKind.GAIN, Side.WEST, Side.NORTH)).isEqualTo(placed(TubeKind.GAIN, Side.EAST, 0, 0));
        assertThat(place(TubeKind.GAIN, Side.SOUTH, Side.EAST)).isEqualTo(placed(TubeKind.GAIN, Side.NORTH, 0, 0));
        assertThat(place(TubeKind.GAIN, Side.EAST, Side.SOUTH)).isEqualTo(placed(TubeKind.GAIN, Side.WEST, 0, 0));
        assertThat(place(TubeKind.GAIN, Side.NORTH, Side.WEST)).isEqualTo(placed(TubeKind.GAIN, Side.SOUTH, 0, 0));
    }

    @Test
    void aStraightTubeRunsAwayFromThePlayer() {
        assertThat(TubePlacement.of(TubeKind.SPEEDUP, 2, 0.3, 1, 0)).isEqualTo(placed(TubeKind.SPEEDUP, Side.EAST, 0, 0));
        assertThat(TubePlacement.of(TubeKind.SPEEDUP, -2, 0.3, -1, 0)).isEqualTo(placed(TubeKind.SPEEDUP, Side.WEST, 0, 0));
        assertThat(TubePlacement.of(TubeKind.SPEEDUP, 0.3, 2, 0, 1)).isEqualTo(placed(TubeKind.SPEEDUP, Side.SOUTH, 0, 0));
        assertThat(TubePlacement.of(TubeKind.SPEEDUP, 0.3, -2, 0, -1)).isEqualTo(placed(TubeKind.SPEEDUP, Side.NORTH, 0, 0));
        assertThat(TubePlacement.of(TubeKind.LINE, -0.3, -2, 0, -1)).isEqualTo(placed(TubeKind.LINE, Side.NORTH, 0, 0));
    }

    @Test
    void whereThePlayerLooksDoesNotTurnAStraightTube() {
        assertThat(TubePlacement.of(TubeKind.SPEEDUP, 2, 0, -1, 0.5)).isEqualTo(placed(TubeKind.SPEEDUP, Side.EAST, 0, 0));
    }

    @Test
    void theClickedBlockIsTheEndForPipes() {
        TubePlacement placement = TubePlacement.of(TubeKind.SPEEDUP, 2, 0, 1, 0);

        assertThat(placement.main()).isEqualTo(Offset.ZERO);
        assertThat(placement.shape().end(0)).isEqualTo(new TubeEnd(Offset.ZERO, Side.WEST));
    }

    @Test
    void aBlockStraightBelowThePlayerStillGetsATube() {
        assertThat(TubePlacement.of(TubeKind.LINE, 0, 0, 0, 0).shape().kind()).isEqualTo(TubeKind.LINE);
        assertThat(TubePlacement.of(TubeKind.CURVE, 0, 0, 0, 0).shape().kind()).isEqualTo(TubeKind.CURVE);
    }

    // the eight ways of the original, by the side the player stands on and the side they look to
    @Test
    void aCurveStartsAtTheClickedBlockAndBendsToTheSideThePlayerLooksAt() {
        assertThat(place(TubeKind.CURVE, Side.NORTH, Side.EAST)).isEqualTo(placed(TubeKind.CURVE, Side.EAST, 2, 2));
        assertThat(place(TubeKind.CURVE, Side.NORTH, Side.WEST)).isEqualTo(placed(TubeKind.CURVE, Side.NORTH, 0, 0));
        assertThat(place(TubeKind.CURVE, Side.WEST, Side.NORTH)).isEqualTo(placed(TubeKind.CURVE, Side.NORTH, 2, -2));
        assertThat(place(TubeKind.CURVE, Side.WEST, Side.SOUTH)).isEqualTo(placed(TubeKind.CURVE, Side.WEST, 0, 0));
        assertThat(place(TubeKind.CURVE, Side.SOUTH, Side.WEST)).isEqualTo(placed(TubeKind.CURVE, Side.WEST, -2, -2));
        assertThat(place(TubeKind.CURVE, Side.SOUTH, Side.EAST)).isEqualTo(placed(TubeKind.CURVE, Side.SOUTH, 0, 0));
        assertThat(place(TubeKind.CURVE, Side.EAST, Side.SOUTH)).isEqualTo(placed(TubeKind.CURVE, Side.SOUTH, -2, 2));
        assertThat(place(TubeKind.CURVE, Side.EAST, Side.NORTH)).isEqualTo(placed(TubeKind.CURVE, Side.EAST, 0, 0));
    }

    @Test
    void theNearEndOfACurveOpensToThePlayerAndTheFarEndToTheSide() {
        for (Side from : new Side[] {Side.NORTH, Side.EAST, Side.SOUTH, Side.WEST}) {
            for (Side looking : new Side[] {Side.NORTH, Side.EAST, Side.SOUTH, Side.WEST}) {
                if (looking == from || looking == from.opposite()) {
                    continue;
                }
                TubePlacement placement = place(TubeKind.CURVE, from, looking);
                TubeShape shape = placement.shape();
                Offset clicked = Offset.ZERO.minus(placement.main());

                int near = shape.endAt(clicked, from).orElseThrow();
                assertThat(shape.end(1 - near).side()).isEqualTo(looking);
            }
        }
    }
}
