package routedpipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;

import routedpipes.adapter.minecraft.menu.PipeConfigMenu;
import routedpipes.domain.crafting.FluidInput;
import routedpipes.domain.crafting.FluidSlots;
import routedpipes.domain.crafting.SatelliteName;
import routedpipes.domain.fluid.FluidBatch;
import routedpipes.domain.fluid.FluidType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// what fluid crafting upgrades add to a crafter: per fluid slot the mB a run takes, and the fluid satellites it goes to
// the fluids themselves are markers in the settings slots from FLUID_FIRST on
public final class FluidCraftingSettings {

    private static final String MODULE_TAG = "LogisticsPipesFluidCrafting";

    private final int[] amounts = new int[FluidSlots.MAX];
    // the satellite of all fluids, and one per fluid with the upgrade for satellites per ingredient
    private String satellite = SatelliteName.NONE;
    private final String[] slotSatellites = Collections.nCopies(FluidSlots.MAX, SatelliteName.NONE).toArray(String[]::new);
    private Runnable changed;

    public FluidCraftingSettings(Runnable changed) {
        this.changed = changed;
    }

    // the settings a module carries, every change is written back to the module before changed runs
    public static FluidCraftingSettings of(ItemStack module, Runnable changed) {
        FluidCraftingSettings settings = new FluidCraftingSettings(changed);
        settings.load(module.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(MODULE_TAG));
        settings.changed = () -> {
            CustomData.update(DataComponents.CUSTOM_DATA, module, tag -> tag.put(MODULE_TAG, settings.save()));
            changed.run();
        };
        return settings;
    }

    public int amount(int slot) {
        return amounts[slot];
    }

    public void setAmount(int slot, int amount) {
        amounts[slot] = FluidSlots.changed(amount, 0);
        changed.run();
    }

    // 0 is the satellite of all fluids, 1 and on the one of a fluid slot
    public String satellite(int field) {
        return field == 0 ? satellite : slotSatellites[field - 1];
    }

    public void setSatellite(int field, String name) {
        if (field == 0) {
            satellite = name;
        } else {
            slotSatellites[field - 1] = name;
        }
        changed.run();
    }

    // what the recipe takes of fluid, config holds the fluid markers
    public List<FluidInput> inputs(Container config, int upgrades, boolean advanced) {
        List<FluidSlots.Slot> slots = new ArrayList<>();
        for (int slot = 0; slot < FluidSlots.MAX; slot++) {
            ItemStack marker = config.getItem(PipeBlockEntity.FLUID_FIRST + slot);
            Optional<FluidType> fluid = FluidContainerItem.fluidIn(marker).map(FluidBatch::type);
            slots.add(fluid.isEmpty() ? FluidSlots.Slot.empty()
                    : new FluidSlots.Slot(fluid, McItemType.batchOf(marker.copyWithCount(1)).type(), amounts[slot], slotSatellites[slot]));
        }
        return FluidSlots.inputs(slots, upgrades, advanced, satellite);
    }

    // for the settings screen, upgrades says how many fluid crafting upgrades there are
    // menu data is sent as short numbers, so an amount goes over in buckets and the rest
    public ContainerData data(IntSupplier upgrades) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (index == PipeConfigMenu.FLUID_UPGRADES) {
                    return upgrades.getAsInt();
                }
                int slot = (index - PipeConfigMenu.FLUID_BUCKETS) % FluidSlots.MAX;
                return index < PipeConfigMenu.FLUID_REST ? amounts[slot] / PipeConfigMenu.BUCKET : amounts[slot] % PipeConfigMenu.BUCKET;
            }

            @Override
            public void set(int index, int value) {
                if (index == PipeConfigMenu.FLUID_UPGRADES) {
                    return;
                }
                int slot = (index - PipeConfigMenu.FLUID_BUCKETS) % FluidSlots.MAX;
                int buckets = index < PipeConfigMenu.FLUID_REST ? value : amounts[slot] / PipeConfigMenu.BUCKET;
                int rest = index < PipeConfigMenu.FLUID_REST ? amounts[slot] % PipeConfigMenu.BUCKET : value;
                setAmount(slot, buckets * PipeConfigMenu.BUCKET + rest);
            }

            @Override
            public int getCount() {
                return PipeConfigMenu.FLUID_DATA_COUNT;
            }
        };
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("Amounts", amounts);
        tag.putString("Satellite", satellite);
        ListTag names = new ListTag();
        for (String name : slotSatellites) {
            names.add(StringTag.valueOf(name));
        }
        tag.put("SlotSatellites", names);
        return tag;
    }

    public void load(CompoundTag tag) {
        int[] saved = tag.getIntArray("Amounts");
        System.arraycopy(saved, 0, amounts, 0, Math.min(saved.length, amounts.length));
        satellite = tag.getString("Satellite");
        ListTag names = tag.getList("SlotSatellites", Tag.TAG_STRING);
        for (int slot = 0; slot < slotSatellites.length; slot++) {
            slotSatellites[slot] = slot < names.size() ? names.getString(slot) : SatelliteName.NONE;
        }
    }
}
