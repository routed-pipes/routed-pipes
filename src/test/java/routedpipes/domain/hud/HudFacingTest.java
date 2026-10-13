package routedpipes.domain.hud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import routedpipes.domain.hud.HudFacing.Spot;
import routedpipes.domain.hud.HudFacing.Vec;
import org.junit.jupiter.api.Test;

class HudFacingTest {

    private static final Vec CENTRE = new Vec(0.5, 0.5, 0.5);
    // south of the block at the height of its middle, looking north at it
    private static final Vec EYE = new Vec(0.5, 0.5, 5.5);

    private static void assertVec(Vec actual, double x, double y, double z) {
        assertThat(actual.x()).isCloseTo(x, within(1e-9));
        assertThat(actual.y()).isCloseTo(y, within(1e-9));
        assertThat(actual.z()).isCloseTo(z, within(1e-9));
    }

    @Test
    void thePanelStandsInFrontOfTheBlockTurnedToThePlayer() {
        HudFacing facing = HudFacing.of(EYE, CENTRE);

        assertVec(facing.origin(), 0.5, 0.5, 0.9);
        assertVec(facing.normal(), 0, 0, 1);
        // looking north east is to the right
        assertVec(facing.right(), 1, 0, 0);
        assertVec(facing.down(), 0, -1, 0);
    }

    @Test
    void fromStraightAboveThePanelStillHasASideways() {
        HudFacing facing = HudFacing.of(new Vec(0.5, 8, 0.5), CENTRE);

        assertVec(facing.normal(), 0, 1, 0);
        assertVec(facing.right(), 1, 0, 0);
        assertThat(facing.down().dot(facing.right())).isCloseTo(0, within(1e-9));
    }

    @Test
    void lookingAtTheMiddleOfThePanelPutsTheCursorThere() {
        Spot spot = HudFacing.of(EYE, CENTRE).cursor(EYE, new Vec(0, 0, -1)).orElseThrow();

        assertThat(spot.x()).isCloseTo(0, within(1e-6));
        assertThat(spot.y()).isCloseTo(0, within(1e-6));
    }

    @Test
    void theCursorIsCountedInHundredthsOfABlockRightAndDown() {
        HudFacing facing = HudFacing.of(EYE, CENTRE);
        // at a spot 0.3 to the east and 0.2 below the middle of the panel
        Vec look = new Vec(0.3, -0.2, 0.9 - 5.5).normalized();

        Spot spot = facing.cursor(EYE, look).orElseThrow();

        assertThat(spot.x()).isCloseTo(30, within(1e-6));
        assertThat(spot.y()).isCloseTo(20, within(1e-6));
    }

    @Test
    void lookingAwayFromThePanelGivesNoCursor() {
        HudFacing facing = HudFacing.of(EYE, CENTRE);

        assertThat(facing.cursor(EYE, new Vec(0, 0, 1))).isEmpty();
        assertThat(facing.cursor(EYE, new Vec(1, 0, 0))).isEmpty();
    }
}
