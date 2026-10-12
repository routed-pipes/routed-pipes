package logisticspipes.adapter.minecraft;

import logisticspipes.domain.fluid.FluidType;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.material.Fluid;

public record McFluidType(Fluid fluid, DataComponentPatch components) implements FluidType {

    public McFluidType(Fluid fluid) {
        this(fluid, DataComponentPatch.EMPTY);
    }
}
