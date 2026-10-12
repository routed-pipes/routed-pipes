package logisticspipes.domain.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import logisticspipes.domain.fluid.FluidType;
import logisticspipes.domain.item.ItemType;

// the fluid slots of a crafter: every fluid crafting upgrade opens one, each takes a fluid, the mB a run needs of it
// and, with the upgrade for satellites per ingredient, a fluid satellite of its own
public final class FluidSlots {

    // more upgrades than this open no more slots
    public static final int MAX = 3;
    // the most one run can take of one fluid, in mB
    public static final int MAX_AMOUNT = 64_000;

    // a slot as a player left it, asItem only matters with a fluid in the slot
    public record Slot(Optional<FluidType> fluid, ItemType asItem, int amount, String satellite) {

        public static Slot empty() {
            return new Slot(Optional.empty(), null, 0, SatelliteName.NONE);
        }
    }

    private FluidSlots() {
    }

    public static int open(int upgrades) {
        return Math.max(0, Math.min(MAX, upgrades));
    }

    // an amount after a press on one of the buttons that change it
    public static int changed(int amount, int change) {
        return (int) Math.max(0, Math.min(MAX_AMOUNT, (long) amount + change));
    }

    // what the recipe really takes: the open slots that have a fluid, an amount and a satellite to send it to
    // fluid only ever goes to a fluid satellite, so a slot without one is left out
    // shared is the satellite of all slots, with advanced every slot goes to its own
    public static List<FluidInput> inputs(List<Slot> slots, int upgrades, boolean advanced, String shared) {
        List<FluidInput> inputs = new ArrayList<>();
        for (int index = 0; index < Math.min(open(upgrades), slots.size()); index++) {
            Slot slot = slots.get(index);
            String satellite = advanced ? slot.satellite() : shared;
            if (slot.fluid().isPresent() && slot.amount() > 0 && !satellite.isEmpty()) {
                inputs.add(new FluidInput(slot.fluid().get(), slot.amount(), satellite, slot.asItem()));
            }
        }
        return List.copyOf(inputs);
    }
}
