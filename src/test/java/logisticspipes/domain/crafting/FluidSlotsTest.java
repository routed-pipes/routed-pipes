package logisticspipes.domain.crafting;

import static logisticspipes.testing.Fixtures.fluid;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import logisticspipes.domain.fluid.FluidType;
import logisticspipes.domain.fluid.PackedFluid;
import org.junit.jupiter.api.Test;

class FluidSlotsTest {

    private static final FluidType LAVA = fluid("lava", 1).type();
    private static final FluidType WATER = fluid("water", 1).type();

    private static FluidSlots.Slot slot(FluidType type, int amount, String satellite) {
        return new FluidSlots.Slot(Optional.of(type), new PackedFluid(type), amount, satellite);
    }

    @Test
    void everyUpgradeOpensASlotUpToThree() {
        assertThat(FluidSlots.open(0)).isZero();
        assertThat(FluidSlots.open(2)).isEqualTo(2);
        assertThat(FluidSlots.open(5)).isEqualTo(FluidSlots.MAX);
    }

    @Test
    void anAmountNeverGoesBelowNothingOrAboveTheMost() {
        assertThat(FluidSlots.changed(5, -10)).isZero();
        assertThat(FluidSlots.changed(900, 100)).isEqualTo(1000);
        assertThat(FluidSlots.changed(FluidSlots.MAX_AMOUNT - 1, 1000)).isEqualTo(FluidSlots.MAX_AMOUNT);
    }

    @Test
    void withoutTheUpgradeForSatellitesPerIngredientAllFluidsGoToTheSharedSatellite() {
        List<FluidSlots.Slot> slots = List.of(slot(LAVA, 1000, "ignored"), slot(WATER, 250, ""));

        assertThat(FluidSlots.inputs(slots, 2, false, "Tank")).containsExactly(
                new FluidInput(LAVA, 1000, "Tank", new PackedFluid(LAVA)), new FluidInput(WATER, 250, "Tank", new PackedFluid(WATER)));
    }

    @Test
    void withTheUpgradeEverySlotGoesToItsOwnSatellite() {
        List<FluidSlots.Slot> slots = List.of(slot(LAVA, 1000, "Lava tank"), slot(WATER, 250, "Water tank"));

        assertThat(FluidSlots.inputs(slots, 2, true, "Tank")).extracting(FluidInput::satellite).containsExactly("Lava tank", "Water tank");
    }

    @Test
    void onlyTheSlotsTheUpgradesOpenCount() {
        List<FluidSlots.Slot> slots = List.of(slot(LAVA, 1000, ""), slot(WATER, 250, ""));

        assertThat(FluidSlots.inputs(slots, 1, false, "Tank")).extracting(FluidInput::fluid).containsExactly(LAVA);
        assertThat(FluidSlots.inputs(slots, 0, false, "Tank")).isEmpty();
    }

    @Test
    void aSlotWithoutFluidAmountOrSatelliteIsLeftOut() {
        List<FluidSlots.Slot> slots = List.of(FluidSlots.Slot.empty(), slot(LAVA, 0, "Tank"), slot(WATER, 100, ""));

        assertThat(FluidSlots.inputs(slots, 3, true, "Shared")).isEmpty();
        assertThat(FluidSlots.inputs(List.of(slot(LAVA, 1000, "")), 1, false, "")).isEmpty();
    }
}
