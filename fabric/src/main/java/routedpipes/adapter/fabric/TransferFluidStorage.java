package routedpipes.adapter.fabric;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import routedpipes.adapter.minecraft.FluidStorage;
import routedpipes.adapter.minecraft.McFluidType;
import routedpipes.domain.fluid.FluidBatch;
import routedpipes.domain.fluid.FluidType;
import routedpipes.domain.fluid.TankAccess;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.ResourceAmount;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// the pipes count in mB, Fabric in droplets
public final class TransferFluidStorage implements FluidStorage {

    static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;

    static FluidVariant variantOf(FluidType type) {
        McFluidType fluid = (McFluidType) type;
        return FluidVariant.of(fluid.fluid(), fluid.components());
    }

    static McFluidType typeOf(FluidVariant variant) {
        return new McFluidType(variant.getFluid(), variant.getComponents());
    }

    static int millibuckets(long droplets) {
        return (int) Math.min(droplets / DROPLETS_PER_MB, Integer.MAX_VALUE);
    }

    private static Optional<FluidBatch> batchOf(ResourceAmount<FluidVariant> content) {
        return content == null || content.resource().isBlank() || millibuckets(content.amount()) == 0
                ? Optional.empty() : Optional.of(new FluidBatch(typeOf(content.resource()), millibuckets(content.amount())));
    }

    private record Tanks(Storage<FluidVariant> storage) implements TankAccess {

        @Override
        public int fill(FluidBatch batch, boolean simulate) {
            try (Transaction transaction = Transaction.openOuter()) {
                long filled = storage.insert(variantOf(batch.type()), batch.amount() * DROPLETS_PER_MB, transaction);
                // only whole mB, the rest would get lost on the way
                long whole = filled / DROPLETS_PER_MB * DROPLETS_PER_MB;
                if (whole != filled) {
                    transaction.abort();
                    return whole == 0 ? 0 : fill(new FluidBatch(batch.type(), millibuckets(whole)), simulate);
                }
                if (!simulate) {
                    transaction.commit();
                }
                return millibuckets(filled);
            }
        }

        @Override
        public int room(FluidType type) {
            return millibuckets(StorageUtil.simulateInsert(storage, variantOf(type), Integer.MAX_VALUE * DROPLETS_PER_MB, null));
        }

        @Override
        public Map<FluidType, Integer> contents() {
            Map<FluidType, Integer> contents = new LinkedHashMap<>();
            for (StorageView<FluidVariant> view : storage.nonEmptyViews()) {
                int amount = millibuckets(view.getAmount());
                if (amount > 0) {
                    contents.merge(typeOf(view.getResource()), amount, (first, second) -> (int) Math.min((long) first + second, Integer.MAX_VALUE));
                }
            }
            return contents;
        }

        @Override
        public int drain(FluidBatch batch, boolean simulate) {
            try (Transaction transaction = Transaction.openOuter()) {
                long drained = storage.extract(variantOf(batch.type()), batch.amount() * DROPLETS_PER_MB, transaction);
                long whole = drained / DROPLETS_PER_MB * DROPLETS_PER_MB;
                if (whole != drained) {
                    transaction.abort();
                    return whole == 0 ? 0 : drain(new FluidBatch(batch.type(), millibuckets(whole)), simulate);
                }
                if (!simulate) {
                    transaction.commit();
                }
                return millibuckets(drained);
            }
        }

        @Override
        public Optional<FluidBatch> drainAny(int upTo, boolean simulate) {
            if (upTo <= 0) {
                return Optional.empty();
            }
            for (StorageView<FluidVariant> view : storage.nonEmptyViews()) {
                FluidBatch wanted = new FluidBatch(typeOf(view.getResource()), upTo);
                int drained = drain(wanted, simulate);
                if (drained > 0) {
                    return Optional.of(new FluidBatch(wanted.type(), drained));
                }
            }
            return Optional.empty();
        }
    }

    @Override
    public Optional<TankAccess> tanks(Level level, BlockPos pos, Direction side) {
        Storage<FluidVariant> storage = net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.find(level, pos, side);
        return storage == null ? Optional.empty() : Optional.of(new Tanks(storage));
    }

    @Override
    public Optional<FluidBatch> contained(ItemStack stack) {
        Storage<FluidVariant> storage = ContainerItemContext.withConstant(stack.copyWithCount(1))
                .find(net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.ITEM);
        return storage == null ? Optional.empty() : batchOf(StorageUtil.findExtractableContent(storage, null));
    }

    @Override
    public ItemStack emptied(ItemStack full) {
        ItemStack[] held = {full.copyWithCount(1)};
        SingleStackStorage slot = new SingleStackStorage() {
            @Override
            protected ItemStack getStack() {
                return held[0];
            }

            @Override
            protected void setStack(ItemStack stack) {
                held[0] = stack;
            }
        };
        Storage<FluidVariant> storage = ContainerItemContext.ofSingleSlot(slot).find(net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.ITEM);
        if (storage == null) {
            // no fluid storage on the item, the crafting remainder is the best guess
            Item remainder = full.getItem().getCraftingRemainingItem();
            return remainder == null ? ItemStack.EMPTY : new ItemStack(remainder);
        }
        try (Transaction transaction = Transaction.openOuter()) {
            ResourceAmount<FluidVariant> content = StorageUtil.findExtractableContent(storage, transaction);
            if (content != null) {
                storage.extract(content.resource(), content.amount(), transaction);
            }
            transaction.commit();
        }
        return held[0];
    }

    @Override
    public Component name(FluidType type) {
        return FluidVariantAttributes.getName(variantOf(type));
    }
}
