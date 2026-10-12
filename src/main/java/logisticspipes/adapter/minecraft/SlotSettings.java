package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.domain.item.FuzzyFlags;
import logisticspipes.domain.item.FuzzyType;
import logisticspipes.domain.item.ItemBatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// what upgrades add to the marker slots of a pipe or module: with a pattern upgrade the inventory slot each one stands for,
// with a fuzzy upgrade the flags that let similar items pass for the one in the slot
public final class SlotSettings {

    private static final String MODULE_TAG = "LogisticsPipesSlotSettings";

    private final int[] patternSlots = new int[PipeBlockEntity.OUTPUT_SLOT];
    // whether what does not fit its slot is sent away instead of put somewhere else in the inventory
    private boolean limited = true;
    // one set of flags for each of the nine markers and for the result of a recipe
    private final int[] fuzzy = new int[PipeConfigMenu.FUZZY_SLOTS];
    private Runnable changed;

    public SlotSettings(Runnable changed) {
        this.changed = changed;
    }

    // the settings a module carries, every change is written back to the module before changed runs
    public static SlotSettings of(ItemStack module, Runnable changed) {
        SlotSettings settings = new SlotSettings(changed);
        settings.load(module.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(MODULE_TAG));
        settings.changed = () -> {
            CustomData.update(DataComponents.CUSTOM_DATA, module, tag -> tag.put(MODULE_TAG, settings.save()));
            changed.run();
        };
        return settings;
    }

    // the inventory slot a marker slot stands for, counted from 0
    public int patternSlot(int marker) {
        return patternSlots[marker];
    }

    public void setPatternSlot(int marker, int slot) {
        patternSlots[marker] = slot;
        changed.run();
    }

    public boolean limited() {
        return limited;
    }

    public void setLimited(boolean enabled) {
        limited = enabled;
        changed.run();
    }

    public FuzzyFlags fuzzyFlags(int slot) {
        return FuzzyFlags.of(fuzzy[slot]);
    }

    public void setFuzzyFlags(int slot, FuzzyFlags flags) {
        fuzzy[slot] = flags.bits();
        changed.run();
    }

    // the marker of a slot as a recipe names it: with flags, and the upgrade for them, it stands for everything that matches
    public ItemBatch batch(int slot, ItemStack marker, boolean upgraded) {
        ItemBatch batch = McItemType.batchOf(marker);
        FuzzyFlags flags = fuzzyFlags(slot);
        return upgraded && flags.any() ? new ItemBatch(new FuzzyType(batch.type(), flags), batch.count()) : batch;
    }

    // the listed items of a sink that have flags, markers are the nine slots in order
    public List<FuzzyType> fuzzyTypes(List<ItemStack> markers) {
        List<FuzzyType> types = new ArrayList<>();
        for (int slot = 0; slot < PipeBlockEntity.OUTPUT_SLOT; slot++) {
            if (!markers.get(slot).isEmpty() && fuzzyFlags(slot).any()) {
                types.add(new FuzzyType(McItemType.batchOf(markers.get(slot)).type(), fuzzyFlags(slot)));
            }
        }
        return types;
    }

    // for the settings screen, pattern and fuzzy say how many of those upgrades there are
    public ContainerData data(IntSupplier pattern, IntSupplier fuzzyUpgrades) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case PipeConfigMenu.EXTRA_PATTERN -> pattern.getAsInt();
                    case PipeConfigMenu.EXTRA_LIMITED -> limited ? 1 : 0;
                    case PipeConfigMenu.EXTRA_FUZZY -> fuzzyUpgrades.getAsInt();
                    default -> index > PipeConfigMenu.EXTRA_FUZZY ? fuzzy[index - PipeConfigMenu.EXTRA_FUZZY_FLAGS]
                            : patternSlots[index - PipeConfigMenu.EXTRA_PATTERN_SLOT];
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == PipeConfigMenu.EXTRA_LIMITED) {
                    setLimited(value != 0);
                } else if (index > PipeConfigMenu.EXTRA_FUZZY) {
                    setFuzzyFlags(index - PipeConfigMenu.EXTRA_FUZZY_FLAGS, FuzzyFlags.of(value));
                } else if (index >= PipeConfigMenu.EXTRA_PATTERN_SLOT && index < PipeConfigMenu.EXTRA_FUZZY) {
                    setPatternSlot(index - PipeConfigMenu.EXTRA_PATTERN_SLOT, value);
                }
            }

            @Override
            public int getCount() {
                return PipeConfigMenu.EXTRA_COUNT;
            }
        };
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("PatternSlots", patternSlots);
        tag.putBoolean("Limited", limited);
        tag.putIntArray("Fuzzy", fuzzy);
        return tag;
    }

    public void load(CompoundTag tag) {
        int[] saved = tag.getIntArray("PatternSlots");
        System.arraycopy(saved, 0, patternSlots, 0, Math.min(saved.length, patternSlots.length));
        int[] savedFuzzy = tag.getIntArray("Fuzzy");
        System.arraycopy(savedFuzzy, 0, fuzzy, 0, Math.min(savedFuzzy.length, fuzzy.length));
        if (tag.contains("Limited")) {
            limited = tag.getBoolean("Limited");
        }
    }
}
