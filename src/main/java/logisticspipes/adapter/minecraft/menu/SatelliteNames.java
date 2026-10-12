package logisticspipes.adapter.minecraft.menu;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.domain.crafting.FluidSlots;
import logisticspipes.domain.crafting.SatelliteName;
import logisticspipes.domain.logistics.PipeKind;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

// the satellite names of a settings menu: the ones that are set and the ones there are to pick from
// menus sync no text, so they reach the client in the data of a stack in a slot that is never shown
public final class SatelliteNames {

    // what the pipe or module behind the menu knows, only the server has one
    public interface Source {
        // empty for none
        String name(int field);

        // the name is clean by now, anything but accepted leaves the field as it was
        SatelliteName.Verdict setName(int field, String name);

        // the satellites of that kind within reach, nearest first
        List<String> offered(PipeKind kind);
    }

    // the name of a satellite pipe itself, or the satellite the bottom row of a crafter goes to
    public static final int FIELD_OWN = 0;
    // plus the ingredient slot, the satellite that slot goes to
    public static final int FIELD_SLOT = 1;
    // the fluid satellite of all fluids of a crafter, plus the fluid slot the one of that fluid
    public static final int FIELD_FLUID = FIELD_SLOT + PipeBlockEntity.OUTPUT_SLOT;
    public static final int FIELD_FLUID_SLOT = FIELD_FLUID + 1;
    public static final int FIELDS = FIELD_FLUID_SLOT + FluidSlots.MAX;
    // more satellites than this are not listed
    private static final int MAX_OFFERED = 64;
    private static final String TAG = "LogisticsPipesSatellites";
    private static final int REFRESH_INTERVAL = 20;

    private final Container carrier = new SimpleContainer(1);
    private final Source source;
    private int ticks;

    // the source is null on the client
    SatelliteNames(Source source) {
        this.source = source;
        refresh();
    }

    Slot slot() {
        return new MarkerSlot(carrier, 0, 0, 0, () -> false);
    }

    private CompoundTag tag() {
        return carrier.getItem(0).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(TAG);
    }

    private static List<String> strings(CompoundTag tag, String key) {
        List<String> strings = new ArrayList<>();
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int index = 0; index < list.size(); index++) {
            strings.add(list.getString(index));
        }
        return strings;
    }

    private static ListTag listOf(List<String> strings) {
        ListTag list = new ListTag();
        strings.forEach(text -> list.add(StringTag.valueOf(text)));
        return list;
    }

    // empty for none, and until the server has said so
    public String name(int field) {
        List<String> names = strings(tag(), "Names");
        return field >= 0 && field < names.size() ? names.get(field) : SatelliteName.NONE;
    }

    public List<String> offered() {
        return strings(tag(), "Offered");
    }

    public List<String> offeredFluid() {
        return strings(tag(), "OfferedFluid");
    }

    // what is within reach changes without anyone touching the menu, so it is looked up again now and then
    void tick() {
        if (source != null && ++ticks % REFRESH_INTERVAL == 0) {
            refresh();
        }
    }

    private void refresh() {
        if (source == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (int field = 0; field < FIELDS; field++) {
            names.add(source.name(field));
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Names", listOf(names));
        tag.put("Offered", listOf(limited(source.offered(PipeKind.SATELLITE))));
        tag.put("OfferedFluid", listOf(limited(source.offered(PipeKind.FLUID_SATELLITE))));
        if (!tag.equals(tag())) {
            ItemStack stack = new ItemStack(Items.PAPER);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.put(TAG, tag));
            carrier.setItem(0, stack);
        }
    }

    private static List<String> limited(List<String> offered) {
        return offered.subList(0, Math.min(MAX_OFFERED, offered.size()));
    }

    // the text comes from a client, so it is cleaned before anyone gets to see it
    SatelliteName.Verdict setName(int field, String text) {
        if (source == null || field < 0 || field >= FIELDS) {
            return SatelliteName.Verdict.BLANK;
        }
        SatelliteName.Verdict verdict = source.setName(field, SatelliteName.clean(text));
        refresh();
        return verdict;
    }
}
