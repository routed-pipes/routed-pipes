package routedpipes.domain.crafting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SatelliteNameTest {

    @Test
    void blanksAroundANameAreDropped() {
        assertThat(SatelliteName.clean("  Furnace top \t")).isEqualTo("Furnace top");
    }

    @Test
    void aNameIsCutToTheLongestAllowed() {
        String cleaned = SatelliteName.clean("x".repeat(SatelliteName.MAX_LENGTH - 1) + " yz");
        assertThat(cleaned).isEqualTo("x".repeat(SatelliteName.MAX_LENGTH - 1));
    }

    @Test
    void theNumberOfAnOldWorldBecomesItsName() {
        assertThat(SatelliteName.ofNumber(7)).isEqualTo("7");
    }

    @Test
    void numberZeroWasNoSatellite() {
        assertThat(SatelliteName.ofNumber(0)).isEqualTo(SatelliteName.NONE);
    }

    @Test
    void aFreshNameIsAccepted() {
        assertThat(SatelliteName.check("Ore", List.of("Coal"))).isEqualTo(SatelliteName.Verdict.ACCEPTED);
    }

    @Test
    void aSatelliteCannotGoWithoutAName() {
        assertThat(SatelliteName.check("   ", List.of())).isEqualTo(SatelliteName.Verdict.BLANK);
    }

    @Test
    void aNameAnotherSatelliteHasIsRefused() {
        assertThat(SatelliteName.check(" Ore ", List.of("Coal", "Ore"))).isEqualTo(SatelliteName.Verdict.TAKEN);
    }
}
