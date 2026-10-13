package routedpipes.adapter.minecraft;

import java.util.Optional;

import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.domain.transport.PipeTransport;
import routedpipes.domain.transport.TransportMode;
import routedpipes.domain.transport.TravellingItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

// the items inside a pipe as nbt, used for saving and for telling clients what to draw
final class TransportTags {

    private TransportTags() {
    }

    static ListTag save(PipeTransport transport, HolderLookup.Provider registries) {
        ListTag items = new ListTag();
        for (TravellingItem item : transport.items()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Id", item.id());
            entry.put("Stack", McItemType.stackOf(item.batch()).save(registries));
            entry.putString("Origin", item.origin().name());
            item.exit().ifPresent(exit -> entry.putString("Exit", exit.name()));
            item.destination().ifPresent(destination -> entry.putUUID("Destination", destination.value()));
            item.nextHop().ifPresent(nextHop -> entry.putUUID("NextHop", nextHop.value()));
            entry.putInt("Progress", item.progress());
            entry.putInt("Speed", item.speed());
            entry.putString("Mode", item.mode().name());
            items.add(entry);
        }
        return items;
    }

    // replaces what is in the pipe, an item that is already shown keeps its place and only gets the rest filled in
    static void load(PipeTransport transport, ListTag items, HolderLookup.Provider registries) {
        PipeTransport shown = new PipeTransport();
        transport.clear().forEach(shown::insert);
        for (int index = 0; index < items.size(); index++) {
            CompoundTag entry = items.getCompound(index);
            Optional<ItemStack> stack = ItemStack.parse(registries, entry.getCompound("Stack"));
            if (stack.isEmpty() || stack.get().isEmpty()) {
                continue;
            }
            long id = entry.getLong("Id");
            TravellingItem item = TravellingItem.restore(
                    id,
                    McItemType.batchOf(stack.get()),
                    Side.valueOf(entry.getString("Origin")),
                    entry.hasUUID("Destination") ? new RouterId(entry.getUUID("Destination")) : null,
                    entry.hasUUID("NextHop") ? new RouterId(entry.getUUID("NextHop")) : null,
                    entry.contains("Mode") ? TransportMode.valueOf(entry.getString("Mode")) : TransportMode.UNCLAIMED,
                    entry.contains("Exit") ? Side.valueOf(entry.getString("Exit")) : null,
                    shown.progressOf(id).orElse(entry.getInt("Progress")),
                    entry.getInt("Speed") > 0 ? entry.getInt("Speed") : TransportMode.UNCLAIMED.speed());
            transport.insert(item);
        }
    }
}
