package routedpipes.adapter.minecraft;

import java.util.HashSet;
import java.util.Set;

import routedpipes.domain.item.ItemType;
import routedpipes.domain.logistics.FirewallRule;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerData;

// the list and the switches of a firewall pipe
public final class FirewallSettings {

    public static final int SLOTS = 36;
    public static final int BLACKLIST = 0;
    public static final int PROVIDING = 1;
    public static final int CRAFTING = 2;
    public static final int SORTING = 3;
    public static final int POWER = 4;
    public static final int FLAGS = 5;

    private final SimpleContainer list = new SimpleContainer(SLOTS);
    // the list stops what is on it, and power is stopped until somebody allows it
    private final boolean[] flags = {true, false, false, false, true};
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return flags[index] ? 1 : 0;
        }

        @Override
        public void set(int index, int value) {
            flags[index] = value != 0;
            changed.run();
        }

        @Override
        public int getCount() {
            return FLAGS;
        }
    };
    private final Runnable changed;

    FirewallSettings(Runnable changed) {
        this.changed = changed;
        list.addListener(container -> changed.run());
    }

    public SimpleContainer list() {
        return list;
    }

    public ContainerData data() {
        return data;
    }

    FirewallRule rule() {
        Set<ItemType> listed = new HashSet<>();
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!list.getItem(slot).isEmpty()) {
                listed.add(McItemType.batchOf(list.getItem(slot)).type().plain());
            }
        }
        return new FirewallRule(listed, flags[BLACKLIST], flags[PROVIDING], flags[CRAFTING], flags[SORTING], flags[POWER]);
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = ContainerTags.save(list, registries);
        byte[] saved = new byte[FLAGS];
        for (int index = 0; index < FLAGS; index++) {
            saved[index] = (byte) (flags[index] ? 1 : 0);
        }
        tag.putByteArray("Flags", saved);
        return tag;
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        ContainerTags.load(list, tag, registries);
        byte[] saved = tag.getByteArray("Flags");
        for (int index = 0; index < Math.min(FLAGS, saved.length); index++) {
            flags[index] = saved[index] != 0;
        }
    }
}
