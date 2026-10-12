package logisticspipes.domain.logistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class ModuleMatchTest {

    @Test
    void sameKindInTheSameSlotMatches() {
        assertThat(ModuleMatch.slots(List.of(ModuleKind.ITEM_SINK, ModuleKind.PROVIDER),
                List.of(ModuleKind.ITEM_SINK, ModuleKind.PROVIDER))).containsExactly(0, 1);
    }

    @Test
    void anotherKindInTheSlotDoesNotMatch() {
        assertThat(ModuleMatch.slots(List.of(ModuleKind.ITEM_SINK, ModuleKind.PROVIDER),
                List.of(ModuleKind.TERMINUS, ModuleKind.PROVIDER))).containsExactly(1);
    }

    @Test
    void theSameKindInAnotherSlotDoesNotMatch() {
        assertThat(ModuleMatch.slots(Arrays.asList(ModuleKind.ITEM_SINK, null),
                Arrays.asList(null, ModuleKind.ITEM_SINK))).isEmpty();
    }

    @Test
    void emptySlotsNeverMatch() {
        assertThat(ModuleMatch.slots(Arrays.asList(null, ModuleKind.EXTRACTOR), Arrays.asList(null, ModuleKind.EXTRACTOR)))
                .containsExactly(1);
    }

    @Test
    void aSmallerChassisTakesOnlyTheSlotsItHas() {
        assertThat(ModuleMatch.slots(List.of(ModuleKind.ITEM_SINK, ModuleKind.ITEM_SINK, ModuleKind.ITEM_SINK),
                List.of(ModuleKind.ITEM_SINK))).containsExactly(0);
        assertThat(ModuleMatch.slots(List.of(ModuleKind.ITEM_SINK),
                List.of(ModuleKind.ITEM_SINK, ModuleKind.ITEM_SINK))).containsExactly(0);
    }
}
