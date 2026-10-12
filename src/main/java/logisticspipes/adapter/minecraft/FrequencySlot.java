package logisticspipes.adapter.minecraft;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

// the frequency card of a system entrance or destination pipe
public final class FrequencySlot {

    private final SimpleContainer card = new SimpleContainer(1) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof FrequencyCardItem;
        }
    };
    // read off the card whenever it changes, the network asks for it with every item
    private Optional<UUID> frequency = Optional.empty();

    FrequencySlot(Runnable changed) {
        card.addListener(container -> {
            frequency = FrequencyCardItem.frequencyOf(card.getItem(0));
            changed.run();
        });
    }

    public Container card() {
        return card;
    }

    // empty without a card and with a blank one
    public Optional<UUID> frequency() {
        return frequency;
    }

    CompoundTag save(HolderLookup.Provider registries) {
        return ContainerTags.save(card, registries);
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        ContainerTags.load(card, tag, registries);
    }
}
