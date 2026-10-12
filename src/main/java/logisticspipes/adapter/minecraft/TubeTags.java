package logisticspipes.adapter.minecraft;

import java.util.Optional;

import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.transport.TransportMode;
import logisticspipes.domain.transport.TubeItem;
import logisticspipes.domain.transport.TubeTransport;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

// the items inside a tube as nbt, used for saving and for telling clients what to draw
final class TubeTags {

    private TubeTags() {
    }

    static ListTag save(TubeTransport transport, HolderLookup.Provider registries) {
        ListTag items = new ListTag();
        for (TubeItem item : transport.items()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Id", item.id());
            entry.put("Stack", McItemType.stackOf(item.batch()).save(registries));
            item.destination().ifPresent(destination -> entry.putUUID("Destination", destination.value()));
            item.nextHop().ifPresent(nextHop -> entry.putUUID("NextHop", nextHop.value()));
            entry.putString("Mode", item.mode().name());
            entry.putInt("Entry", item.entry());
            entry.putInt("Progress", item.progress());
            items.add(entry);
        }
        return items;
    }

    // replaces what is in the tube, an item that is already shown keeps its place
    static void load(TubeTransport transport, ListTag items, HolderLookup.Provider registries) {
        TubeTransport shown = new TubeTransport();
        transport.clear().forEach(shown::insert);
        for (int index = 0; index < items.size(); index++) {
            CompoundTag entry = items.getCompound(index);
            Optional<ItemStack> stack = ItemStack.parse(registries, entry.getCompound("Stack"));
            if (stack.isEmpty() || stack.get().isEmpty()) {
                continue;
            }
            long id = entry.getLong("Id");
            transport.insert(TubeItem.restore(
                    id,
                    McItemType.batchOf(stack.get()),
                    entry.hasUUID("Destination") ? new RouterId(entry.getUUID("Destination")) : null,
                    entry.hasUUID("NextHop") ? new RouterId(entry.getUUID("NextHop")) : null,
                    TransportMode.valueOf(entry.getString("Mode")),
                    entry.getInt("Entry"),
                    shown.progressOf(id).orElse(entry.getInt("Progress"))));
        }
    }
}
