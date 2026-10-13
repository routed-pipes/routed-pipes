package routedpipes.domain.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

class HudSettingsTest {

    @Test
    void everythingIsShownAtFirst() {
        for (HudCategory category : HudCategory.values()) {
            assertThat(HudSettings.ALL.shows(category)).isTrue();
        }
    }

    @Test
    void togglingHidesAndShowsAgain() {
        HudSettings noPower = HudSettings.ALL.toggled(HudCategory.POWER);

        assertThat(noPower.shows(HudCategory.POWER)).isFalse();
        assertThat(noPower.shows(HudCategory.CHASSIS)).isTrue();
        assertThat(noPower.toggled(HudCategory.POWER)).isEqualTo(HudSettings.ALL);
    }

    @Test
    void survivesTheWayThroughBits() {
        HudSettings some = new HudSettings(Set.of(HudCategory.CRAFTING, HudCategory.SATELLITE));

        assertThat(HudSettings.ofBits(some.bits())).isEqualTo(some);
        assertThat(HudSettings.ofBits(0).shown()).isEmpty();
        assertThat(HudSettings.ofBits(HudSettings.ALL.bits())).isEqualTo(HudSettings.ALL);
    }
}
