package logisticspipes.domain.tube;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import logisticspipes.domain.routing.Side;
import org.junit.jupiter.api.Test;

class TubeShapeTest {

    private static final List<Side> FACINGS = List.of(Side.NORTH, Side.EAST, Side.SOUTH, Side.WEST);

    private static List<TubeShape> everyShape() {
        List<TubeShape> shapes = new ArrayList<>();
        for (TubeKind kind : TubeKind.values()) {
            for (Side facing : FACINGS) {
                shapes.add(new TubeShape(kind, facing));
            }
        }
        return shapes;
    }

    private static List<Offset> blocks(TubeShape shape) {
        List<Offset> blocks = new ArrayList<>(offsets(shape));
        blocks.add(Offset.ZERO);
        return blocks;
    }

    private static List<Offset> offsets(TubeShape shape) {
        return shape.parts().stream().map(TubePart::offset).toList();
    }

    // the middle of the face an end opens through
    private static Point opening(TubeEnd end) {
        Offset out = Offset.of(end.side());
        return new Point(end.block().x() + 0.5 + out.x() * 0.5, end.block().y() + 0.5 + out.y() * 0.5,
                end.block().z() + 0.5 + out.z() * 0.5);
    }

    private static void assertSamePoint(Point actual, Point expected) {
        assertThat(actual.x()).isCloseTo(expected.x(), within(1e-9));
        assertThat(actual.y()).isCloseTo(expected.y(), within(1e-9));
        assertThat(actual.z()).isCloseTo(expected.z(), within(1e-9));
    }

    @Test
    void aLineTubeIsOneBlockOpenAtBothEndsOfItsAxis() {
        TubeShape line = new TubeShape(TubeKind.LINE, Side.EAST);

        assertThat(line.parts()).isEmpty();
        assertThat(line.end(0)).isEqualTo(new TubeEnd(Offset.ZERO, Side.WEST));
        assertThat(line.end(1)).isEqualTo(new TubeEnd(Offset.ZERO, Side.EAST));
        assertThat(TubeKind.LINE.length()).isEqualTo(60);
    }

    @Test
    void aSpeedupTubeRunsFourBlocksTheWayItFaces() {
        assertThat(offsets(new TubeShape(TubeKind.SPEEDUP, Side.NORTH)))
                .containsExactly(new Offset(0, 0, -1), new Offset(0, 0, -2), new Offset(0, 0, -3));
        assertThat(offsets(new TubeShape(TubeKind.SPEEDUP, Side.EAST)))
                .containsExactly(new Offset(1, 0, 0), new Offset(2, 0, 0), new Offset(3, 0, 0));
        assertThat(offsets(new TubeShape(TubeKind.SPEEDUP, Side.SOUTH)))
                .containsExactly(new Offset(0, 0, 1), new Offset(0, 0, 2), new Offset(0, 0, 3));
        assertThat(offsets(new TubeShape(TubeKind.SPEEDUP, Side.WEST)))
                .containsExactly(new Offset(-1, 0, 0), new Offset(-2, 0, 0), new Offset(-3, 0, 0));
        assertThat(TubeKind.SPEEDUP.length()).isEqualTo(240);
    }

    @Test
    void theMainBlockOfASpeedupTubeIsItsEndForPipes() {
        TubeShape speedup = new TubeShape(TubeKind.SPEEDUP, Side.WEST);

        assertThat(speedup.end(0)).isEqualTo(new TubeEnd(Offset.ZERO, Side.EAST));
        assertThat(speedup.end(1)).isEqualTo(new TubeEnd(new Offset(-3, 0, 0), Side.WEST));
        assertThat(TubeKind.SPEEDUP.pipeEnd(0)).isTrue();
        assertThat(TubeKind.SPEEDUP.pipeEnd(1)).isFalse();
        assertThat(TubeKind.LINE.pipeEnd(0)).isFalse();
        assertThat(TubeKind.LINE.pipeEnd(1)).isFalse();
    }

    @Test
    void aCurveTubeTurnsFromItsMainBlockToTheLeftOverSevenBlocks() {
        TubeShape curve = new TubeShape(TubeKind.CURVE, Side.NORTH);

        assertThat(curve.parts()).containsExactly(
                new TubePart(new Offset(-1, 0, 0), PartType.CURVE_INNER_A), new TubePart(new Offset(0, 0, 1), PartType.CURVE_OUT_A),
                new TubePart(new Offset(-1, 0, 1), PartType.NON_SHARE), new TubePart(new Offset(-2, 0, 1), PartType.CURVE_INNER_B),
                new TubePart(new Offset(-1, 0, 2), PartType.CURVE_OUT_B), new TubePart(new Offset(-2, 0, 2), PartType.NON_SHARE));
        assertThat(curve.end(0)).isEqualTo(new TubeEnd(Offset.ZERO, Side.NORTH));
        assertThat(curve.end(1)).isEqualTo(new TubeEnd(new Offset(-2, 0, 2), Side.WEST));
        // a quarter of a circle with radius 2.5
        assertThat(TubeKind.CURVE.length()).isEqualTo(236);
        assertThat(TubeKind.CURVE.weight()).isEqualTo(4);
    }

    @Test
    void aCurveTubeTurnsWithTheSideItFaces() {
        // the far block and the side it opens to, as the original has them for its four ways to lie
        assertThat(new TubeShape(TubeKind.CURVE, Side.EAST).end(1)).isEqualTo(new TubeEnd(new Offset(-2, 0, -2), Side.NORTH));
        assertThat(new TubeShape(TubeKind.CURVE, Side.SOUTH).end(1)).isEqualTo(new TubeEnd(new Offset(2, 0, -2), Side.EAST));
        assertThat(new TubeShape(TubeKind.CURVE, Side.WEST).end(1)).isEqualTo(new TubeEnd(new Offset(2, 0, 2), Side.SOUTH));
        assertThat(offsets(new TubeShape(TubeKind.CURVE, Side.EAST))).containsExactly(new Offset(0, 0, -1), new Offset(-1, 0, 0),
                new Offset(-1, 0, -1), new Offset(-1, 0, -2), new Offset(-2, 0, -1), new Offset(-2, 0, -2));
    }

    @Test
    void anItemInACurveKeepsItsDistanceFromTheMiddleOfTheCircle() {
        TubeShape curve = new TubeShape(TubeKind.CURVE, Side.NORTH);

        for (int step = 0; step <= 10; step++) {
            Point point = curve.pointAt(step / 10.0);
            assertThat(Math.hypot(point.x() + 2, point.z())).isCloseTo(2.5, within(1e-9));
            assertThat(point.y()).isEqualTo(0.5);
        }
        // half way round
        assertThat(curve.pointAt(0.5).x()).isCloseTo(-2 + 2.5 * Math.sqrt(0.5), within(1e-9));
    }

    @Test
    void curvesShareBlocksWhereOneRunsInsideTheOther() {
        assertThat(PartType.canShare(List.of(), PartType.NON_SHARE)).isTrue();
        assertThat(PartType.canShare(List.of(PartType.CURVE_OUT_A), PartType.CURVE_INNER_A)).isTrue();
        assertThat(PartType.canShare(List.of(PartType.CURVE_INNER_A), PartType.CURVE_OUT_A)).isTrue();
        assertThat(PartType.canShare(List.of(PartType.CURVE_INNER_B), PartType.CURVE_OUT_B)).isTrue();
        assertThat(PartType.canShare(List.of(PartType.CURVE_OUT_A), PartType.CURVE_OUT_A)).isFalse();
        assertThat(PartType.canShare(List.of(PartType.CURVE_OUT_A), PartType.CURVE_INNER_B)).isFalse();
        assertThat(PartType.canShare(List.of(PartType.NON_SHARE), PartType.CURVE_INNER_A)).isFalse();
        assertThat(PartType.canShare(List.of(PartType.CURVE_OUT_A), PartType.NON_SHARE)).isFalse();
        // never more than two
        assertThat(PartType.canShare(List.of(PartType.CURVE_OUT_A, PartType.CURVE_INNER_A), PartType.CURVE_INNER_A)).isFalse();
    }

    @Test
    void findsTheEndByBlockAndSide() {
        TubeShape speedup = new TubeShape(TubeKind.SPEEDUP, Side.NORTH);

        assertThat(speedup.endAt(Offset.ZERO, Side.SOUTH)).isEqualTo(OptionalInt.of(0));
        assertThat(speedup.endAt(new Offset(0, 0, -3), Side.NORTH)).isEqualTo(OptionalInt.of(1));
        assertThat(speedup.endAt(new Offset(0, 0, -3), Side.SOUTH)).isEmpty();
        assertThat(speedup.endAt(new Offset(0, 0, -2), Side.NORTH)).isEmpty();
        assertThat(speedup.endAt(Offset.ZERO, Side.EAST)).isEmpty();
    }

    @Test
    void thePathStartsAndStopsInTheMiddleOfTheOpenings() {
        for (TubeShape shape : everyShape()) {
            assertSamePoint(shape.pointAt(0), opening(shape.end(0)));
            assertSamePoint(shape.pointAt(1), opening(shape.end(1)));
        }
    }

    @Test
    void thePathNeverLeavesTheBlocksOfTheTube() {
        for (TubeShape shape : everyShape()) {
            List<Offset> blocks = blocks(shape);
            for (int step = 1; step < 200; step++) {
                Point point = shape.pointAt(step / 200.0);
                Offset block = new Offset((int) Math.floor(point.x()), (int) Math.floor(point.y()), (int) Math.floor(point.z()));
                assertThat(blocks).as("%s at %s", shape, point).contains(block);
            }
        }
    }

    @Test
    void theEndsAreInBlocksOfTheTubeAndOpenOutOfIt() {
        for (TubeShape shape : everyShape()) {
            List<Offset> blocks = blocks(shape);
            for (int index = 0; index < 2; index++) {
                TubeEnd end = shape.end(index);
                assertThat(blocks).contains(end.block());
                assertThat(blocks).doesNotContain(end.block().plus(Offset.of(end.side())));
            }
        }
    }

    @Test
    void everyBlockOfATubeHoldsSomeOfIt() {
        for (TubeShape shape : everyShape()) {
            for (Offset block : blocks(shape)) {
                assertThat(shape.boxesIn(block)).as("%s in %s", shape, block).isNotEmpty();
            }
        }
    }

    @Test
    void aStraightTubeIsAsWideAsAPipeAndFillsItsBlocksFromEndToEnd() {
        Box alongZ = new Box(0.1875, 0.1875, 0, 0.8125, 0.8125, 1);
        Box alongX = new Box(0, 0.1875, 0.1875, 1, 0.8125, 0.8125);

        assertThat(new TubeShape(TubeKind.LINE, Side.NORTH).boxesIn(Offset.ZERO)).containsExactly(alongZ);
        assertThat(new TubeShape(TubeKind.SPEEDUP, Side.SOUTH).boxesIn(new Offset(0, 0, 2))).containsExactly(alongZ);
        assertThat(new TubeShape(TubeKind.SPEEDUP, Side.WEST).boxesIn(new Offset(-3, 0, 0))).containsExactly(alongX);
    }

    @Test
    void aTubeCountsAnEighthOfAPipePerBlock() {
        assertThat(TubeKind.PIPE_WEIGHT).isEqualTo(8);
        assertThat(TubeKind.LINE.weight()).isEqualTo(1);
        assertThat(TubeKind.SPEEDUP.weight()).isEqualTo(4);
    }

    @Test
    void tubesDoNotStandOnEnd() {
        assertThatThrownBy(() -> new TubeShape(TubeKind.LINE, Side.UP)).isInstanceOf(IllegalArgumentException.class);
    }
}
