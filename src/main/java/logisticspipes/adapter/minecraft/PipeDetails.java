package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.Shortened;
import logisticspipes.domain.logistics.SinkRule;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.world.item.ItemStack;

// what a routed pipe takes in and what it still has to craft, cut short enough for a tooltip
// made on the server and sent to the client as a tag
public record PipeDetails(Shortened<Entry> listed, boolean defaultRoute, Shortened<Entry> modules, Shortened<Entry> orders) {

    // count only matters for orders
    public record Entry(ItemStack stack, int count) {
    }

    private static final int MAX_SHOWN = 3;

    public static PipeDetails of(PipeBlockEntity pipe) {
        Set<ItemType> wanted = new LinkedHashSet<>();
        boolean defaultRoute = false;
        for (SinkRule rule : pipe.sinkRules()) {
            wanted.addAll(rule.wants());
            defaultRoute |= rule instanceof ItemSink sink && sink.defaultRoute();
        }
        // filters are sets, sorted so the tooltip does not shuffle
        List<Entry> listed = wanted.stream().map(type -> new Entry(McItemType.stackOf(new ItemBatch(type, 1)), 1))
                .sorted(Comparator.comparing(entry -> BuiltInRegistries.ITEM.getKey(entry.stack().getItem()).toString())).toList();
        List<Entry> modules = new ArrayList<>();
        for (int slot = 0; slot < pipe.modules().getContainerSize(); slot++) {
            if (!pipe.modules().getItem(slot).isEmpty()) {
                modules.add(new Entry(pipe.modules().getItem(slot).copyWithCount(1), 1));
            }
        }
        List<Entry> orders = new ArrayList<>();
        pipe.outputOrders().owedByType().forEach((type, count) ->
                orders.add(new Entry(McItemType.stackOf(new ItemBatch(type, 1)), count)));
        return new PipeDetails(Shortened.of(listed, MAX_SHOWN), defaultRoute, Shortened.of(modules, MAX_SHOWN),
                Shortened.of(orders, MAX_SHOWN));
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        save(tag, "Listed", listed, registries);
        tag.putBoolean("DefaultRoute", defaultRoute);
        save(tag, "Modules", modules, registries);
        save(tag, "Orders", orders, registries);
        return tag;
    }

    private static void save(CompoundTag tag, String name, Shortened<Entry> entries, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries.shown()) {
            CompoundTag saved = new CompoundTag();
            saved.put("Stack", entry.stack().save(registries));
            saved.putInt("Count", entry.count());
            list.add(saved);
        }
        tag.put(name, list);
        tag.putInt(name + "More", entries.more());
    }

    public static PipeDetails load(CompoundTag tag, HolderLookup.Provider registries) {
        return new PipeDetails(load(tag, "Listed", registries), tag.getBoolean("DefaultRoute"), load(tag, "Modules", registries),
                load(tag, "Orders", registries));
    }

    private static Shortened<Entry> load(CompoundTag tag, String name, HolderLookup.Provider registries) {
        List<Entry> entries = new ArrayList<>();
        ListTag list = tag.getList(name, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag saved = list.getCompound(index);
            ItemStack.parse(registries, saved.getCompound("Stack"))
                    .ifPresent(stack -> entries.add(new Entry(stack, saved.getInt("Count"))));
        }
        return new Shortened<>(entries, tag.getInt(name + "More"));
    }

    // one line per thing there is something to say about
    public List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        if (!modules.isEmpty()) {
            lines.add(Component.translatable("logisticspipes.status.modules", joined(modules, false)));
        }
        if (!listed.isEmpty()) {
            lines.add(Component.translatable("logisticspipes.status.takes", joined(listed, false)));
        }
        if (defaultRoute) {
            lines.add(Component.translatable("logisticspipes.status.default_route"));
        }
        if (!orders.isEmpty()) {
            lines.add(Component.translatable("logisticspipes.status.orders", joined(orders, true)));
        }
        return lines;
    }

    private static Component joined(Shortened<Entry> entries, boolean counted) {
        List<Component> parts = new ArrayList<>();
        for (Entry entry : entries.shown()) {
            parts.add(counted
                    ? Component.translatable("logisticspipes.status.order_entry", entry.count(), entry.stack().getHoverName())
                    : entry.stack().getHoverName());
        }
        if (entries.more() > 0) {
            parts.add(Component.translatable("logisticspipes.status.more", entries.more()));
        }
        return ComponentUtils.formatList(parts, Component.literal(", "));
    }
}
