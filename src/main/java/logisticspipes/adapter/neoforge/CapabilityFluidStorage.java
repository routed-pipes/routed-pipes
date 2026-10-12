package logisticspipes.adapter.neoforge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import logisticspipes.adapter.minecraft.FluidStorage;
import logisticspipes.adapter.minecraft.McFluidType;
import logisticspipes.domain.fluid.FluidBatch;
import logisticspipes.domain.fluid.FluidType;
import logisticspipes.domain.fluid.TankAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public final class CapabilityFluidStorage implements FluidStorage {

    static FluidStack stackOf(FluidBatch batch) {
        McFluidType type = (McFluidType) batch.type();
        return new FluidStack(type.fluid().builtInRegistryHolder(), batch.amount(), type.components());
    }

    static McFluidType typeOf(FluidStack stack) {
        return new McFluidType(stack.getFluid(), stack.getComponentsPatch());
    }

    private static Optional<FluidBatch> batchOf(FluidStack stack) {
        return stack.isEmpty() ? Optional.empty() : Optional.of(new FluidBatch(typeOf(stack), stack.getAmount()));
    }

    private static FluidAction action(boolean simulate) {
        return simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE;
    }

    private record Tanks(IFluidHandler handler) implements TankAccess {

        @Override
        public int fill(FluidBatch batch, boolean simulate) {
            return handler.fill(stackOf(batch), action(simulate));
        }

        @Override
        public int room(FluidType type) {
            FluidStack wanted = stackOf(new FluidBatch(type, 1));
            long room = 0;
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack held = handler.getFluidInTank(tank);
                if (held.isEmpty() || FluidStack.isSameFluidSameComponents(held, wanted)) {
                    room += handler.getTankCapacity(tank) - held.getAmount();
                }
            }
            return (int) Math.min(room, Integer.MAX_VALUE);
        }

        @Override
        public Map<FluidType, Integer> contents() {
            Map<FluidType, Integer> contents = new LinkedHashMap<>();
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack held = handler.getFluidInTank(tank);
                if (!held.isEmpty()) {
                    contents.merge(typeOf(held), held.getAmount(), (first, second) -> (int) Math.min((long) first + second, Integer.MAX_VALUE));
                }
            }
            return contents;
        }

        @Override
        public int drain(FluidBatch batch, boolean simulate) {
            return handler.drain(stackOf(batch), action(simulate)).getAmount();
        }

        @Override
        public Optional<FluidBatch> drainAny(int upTo, boolean simulate) {
            return batchOf(handler.drain(upTo, action(simulate)));
        }
    }

    @Override
    public Optional<TankAccess> tanks(Level level, BlockPos pos, Direction side) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        return handler == null || handler.getTanks() == 0 ? Optional.empty() : Optional.of(new Tanks(handler));
    }

    @Override
    public Optional<FluidBatch> contained(ItemStack stack) {
        return FluidUtil.getFluidContained(stack).flatMap(CapabilityFluidStorage::batchOf);
    }

    @Override
    public Component name(FluidType type) {
        return stackOf(new FluidBatch(type, 1)).getHoverName();
    }
}
