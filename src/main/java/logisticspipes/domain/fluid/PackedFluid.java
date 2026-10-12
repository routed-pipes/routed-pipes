package logisticspipes.domain.fluid;

import logisticspipes.domain.item.ItemType;

// a fluid where the network counts items, a batch of it is that many mB
public record PackedFluid(FluidType fluid) implements ItemType {

    @Override
    public boolean packedFluid() {
        return true;
    }
}
