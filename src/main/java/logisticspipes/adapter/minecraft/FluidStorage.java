package logisticspipes.adapter.minecraft;

import java.util.Optional;

import logisticspipes.domain.fluid.FluidBatch;
import logisticspipes.domain.fluid.FluidType;
import logisticspipes.domain.fluid.TankAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// access to tanks in the world and to what is known about fluids, every loader has its own api for this
public interface FluidStorage {

    // the tanks of the block as seen through that side, empty if it has none
    Optional<TankAccess> tanks(Level level, BlockPos pos, Direction side);

    // the fluid in an item like a bucket
    Optional<FluidBatch> contained(ItemStack stack);

    // what is left of one such item once its fluid is poured out, like an empty bucket or an empty tank
    ItemStack emptied(ItemStack full);

    Component name(FluidType type);
}
