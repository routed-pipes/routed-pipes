package logisticspipes.domain.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class HudRangeTest {

    @Test
    void panelsShowBetweenThreeQuartersOfABlockAndFifteenBlocks() {
        assertThat(HudRange.shows(0.75)).isFalse();
        assertThat(HudRange.shows(0.76)).isTrue();
        assertThat(HudRange.shows(14.99)).isTrue();
        assertThat(HudRange.shows(15)).isFalse();
    }

    @Test
    void theNearestComeFirstAndWhatIsOutOfRangeIsLeftOut() {
        assertThat(HudRange.nearest(List.of(20.0, 3.0, 0.5, 9.0, 1.0), distance -> distance)).containsExactly(1.0, 3.0, 9.0);
    }

    @Test
    void inACrowdOnlyTheNearestGetAPanel() {
        List<Double> crowd = new ArrayList<>();
        for (int index = 100; index > 0; index--) {
            crowd.add(1 + index / 10.0);
        }

        List<Double> nearest = HudRange.nearest(crowd, distance -> distance);

        assertThat(nearest).hasSize(HudRange.MAX_PANELS);
        assertThat(nearest.get(0)).isEqualTo(1.1);
        assertThat(nearest.get(HudRange.MAX_PANELS - 1)).isEqualTo(1 + HudRange.MAX_PANELS / 10.0);
    }
}
