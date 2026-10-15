package routedpipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import routedpipes.adapter.minecraft.menu.PipeConfigMenu;
import routedpipes.adapter.minecraft.menu.SatelliteNames;
import routedpipes.domain.crafting.Cleanup;
import routedpipes.domain.crafting.CraftingSetup;
import routedpipes.domain.crafting.FluidInput;
import routedpipes.domain.crafting.Recipe;
import routedpipes.domain.crafting.SatelliteName;
import routedpipes.domain.item.FuzzyType;
import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.logistics.FuzzySink;
import routedpipes.domain.logistics.ItemSink;
import routedpipes.domain.logistics.PatternSupply;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.ProviderMode;
import routedpipes.domain.logistics.ProviderSettings;
import routedpipes.domain.logistics.SinkRule;
import routedpipes.domain.logistics.Supply;
import routedpipes.domain.logistics.SupplyMode;
import routedpipes.domain.logistics.UpgradeKind;
import routedpipes.domain.logistics.VoidSink;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

// what is set on the pipe itself and not on a module: its marker slots, the satellites it names
// and the switches of its settings screen, and what a pipe without modules does with them
final class PipeSettings {

    private final PipeBlockEntity pipe;
    private final SimpleContainer config = new SimpleContainer(PipeBlockEntity.CONFIG_SLOTS);
    private final SlotSettings slotSettings;
    private final FluidCraftingSettings fluidCrafting;
    // PipeTags saves and loads the plain values as they are, so they are not private
    // the name of a satellite, on a crafting pipe the satellite the bottom row of its recipe goes to
    String satelliteName = SatelliteName.NONE;
    boolean defaultRoute;
    // provider mode of a provider pipe, supply mode of a supplier pipe
    int mode;
    boolean exclude;
    // how eagerly a crafting pipe is used, higher first
    int priority;
    // with the upgrade for it every ingredient slot can have a satellite of its own
    final String[] slotSatellites = Collections.nCopies(PipeBlockEntity.OUTPUT_SLOT, SatelliteName.NONE).toArray(String[]::new);
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case PipeConfigMenu.DATA_DEFAULT_ROUTE -> defaultRoute ? 1 : 0;
                case PipeConfigMenu.DATA_MODE -> mode;
                case PipeConfigMenu.DATA_EXCLUDE -> exclude ? 1 : 0;
                case PipeConfigMenu.DATA_PRIORITY -> priority;
                case PipeConfigMenu.DATA_BYPRODUCT -> pipe.upgradeCount(UpgradeKind.CRAFTING_BYPRODUCT);
                case PipeConfigMenu.DATA_CLEANUP -> pipe.upgradeCount(UpgradeKind.CRAFTING_CLEANUP);
                default -> pipe.upgradeCount(UpgradeKind.ADVANCED_SATELLITE);
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case PipeConfigMenu.DATA_DEFAULT_ROUTE -> defaultRoute = value != 0;
                case PipeConfigMenu.DATA_MODE -> mode = value;
                case PipeConfigMenu.DATA_EXCLUDE -> exclude = value != 0;
                case PipeConfigMenu.DATA_PRIORITY -> priority = value;
                default -> {
                }
            }
            pipe.settingsChanged();
        }

        @Override
        public int getCount() {
            return PipeConfigMenu.DATA_COUNT;
        }
    };

    PipeSettings(PipeBlockEntity pipe) {
        this.pipe = pipe;
        slotSettings = new SlotSettings(pipe::settingsChanged);
        fluidCrafting = new FluidCraftingSettings(pipe::settingsChanged);
        if (pipe.kind() == PipeKind.SUPPLIER) {
            mode = SupplyMode.BULK50.ordinal();
        }
        // a crafter that cleans up keeps what is on its list unless told otherwise
        exclude = pipe.kind() == PipeKind.CRAFTING;
    }

    SimpleContainer config() {
        return config;
    }

    SlotSettings slotSettings() {
        return slotSettings;
    }

    FluidCraftingSettings fluidCrafting() {
        return fluidCrafting;
    }

    // the switches of the settings screen
    ContainerData data() {
        return data;
    }

    SupplyMode supplyMode() {
        SupplyMode[] modes = SupplyMode.values();
        return modes[Math.floorMod(mode, modes.length)];
    }

    ProviderSettings providerSettings() {
        ProviderMode[] modes = ProviderMode.values();
        return new ProviderSettings(modes[Math.floorMod(mode, modes.length)], supplyTargets().keySet(), exclude);
    }

    // no questions asked, a name a player types goes through satelliteSettings
    // like typing it in the screen: a satellite only takes a name no other satellite in reach has, false if refused
    boolean pasteSatelliteName(String name) {
        if (pipe.kind() == PipeKind.SATELLITE || pipe.kind() == PipeKind.FLUID_SATELLITE) {
            if (SatelliteName.check(name, pipe.network().satelliteNames(pipe.routerId(), pipe.kind())) != SatelliteName.Verdict.ACCEPTED) {
                return false;
            }
        }
        setSatelliteName(name);
        return true;
    }

    void setSatelliteName(String name) {
        satelliteName = SatelliteName.clean(name);
        pipe.settingsChanged();
    }

    void setSlotSatellite(int slot, String name) {
        slotSatellites[slot] = SatelliteName.clean(name);
        pipe.settingsChanged();
    }

    // what a settings copier takes along besides the markers and the plain switches
    CompoundTag copyPerSlot() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Priority", priority);
        ListTag names = new ListTag();
        for (String name : slotSatellites) {
            names.add(StringTag.valueOf(name));
        }
        tag.put("SlotSatellites", names);
        tag.put("SlotSettings", slotSettings.save());
        return tag;
    }

    void pastePerSlot(CompoundTag tag) {
        priority = tag.getInt("Priority");
        ListTag names = tag.getList("SlotSatellites", Tag.TAG_STRING);
        for (int slot = 0; slot < slotSatellites.length && slot < names.size(); slot++) {
            slotSatellites[slot] = SatelliteName.clean(names.getString(slot));
        }
        slotSettings.load(tag.getCompound("SlotSettings"));
        pipe.settingsChanged();
    }

    // what the settings screen shows and sets of satellite names: a satellite its own, a crafter the ones it sends to
    SatelliteNames.Source satelliteSettings() {
        return new SatelliteNames.Source() {
            @Override
            public String name(int field) {
                if (field >= SatelliteNames.FIELD_FLUID) {
                    return fluidCrafting.satellite(field - SatelliteNames.FIELD_FLUID);
                }
                return field == SatelliteNames.FIELD_OWN ? satelliteName : slotSatellites[field - SatelliteNames.FIELD_SLOT];
            }

            @Override
            public SatelliteName.Verdict setName(int field, String name) {
                if (field >= SatelliteNames.FIELD_FLUID) {
                    fluidCrafting.setSatellite(field - SatelliteNames.FIELD_FLUID, name);
                    return SatelliteName.Verdict.ACCEPTED;
                }
                if (field != SatelliteNames.FIELD_OWN) {
                    setSlotSatellite(field - SatelliteNames.FIELD_SLOT, name);
                    return SatelliteName.Verdict.ACCEPTED;
                }
                // a crafter may point at any satellite or at none, a satellite needs a name of its own
                SatelliteName.Verdict verdict = pipe.kind() == PipeKind.CRAFTING ? SatelliteName.Verdict.ACCEPTED
                        : SatelliteName.check(name, pipe.network().satelliteNames(pipe.routerId(), pipe.kind()));
                if (verdict == SatelliteName.Verdict.ACCEPTED) {
                    setSatelliteName(name);
                }
                return verdict;
            }

            @Override
            public List<String> offered(PipeKind satellites) {
                return pipe.kind() == PipeKind.CRAFTING ? pipe.network().satelliteNames(pipe.routerId(), satellites) : List.of();
            }
        };
    }

    // copies the recipe of a crafting table next to the pipe into the pipe settings
    void importRecipe() {
        importRecipe(config);
    }

    void importRecipe(Container target) {
        CraftingTableBlockEntity.beside(pipe.getLevel(), pipe.getBlockPos()).ifPresent(table -> {
            for (int slot = 0; slot < PipeBlockEntity.OUTPUT_SLOT; slot++) {
                target.setItem(slot, table.pattern().getItem(slot).copy());
            }
            target.setItem(PipeBlockEntity.OUTPUT_SLOT, table.result());
        });
    }

    // a recipe slot as the planner sees it, with fuzzy flags it stands for everything that matches
    private ItemBatch marked(int slot) {
        return slotSettings.batch(slot, config.getItem(slot), pipe.hasUpgrade(UpgradeKind.FUZZY));
    }

    // what a crafting pipe crafts
    List<CraftingSetup> crafts() {
        ItemStack extra = config.getItem(PipeBlockEntity.BYPRODUCT_SLOT);
        Optional<ItemBatch> byproduct = extra.isEmpty() || pipe.upgradeCount(UpgradeKind.CRAFTING_BYPRODUCT) == 0
                ? Optional.empty() : Optional.of(McItemType.batchOf(extra));
        List<FluidInput> fluids = fluidCrafting.inputs(config, pipe.upgradeCount(UpgradeKind.FLUID_CRAFTING),
                pipe.hasUpgrade(UpgradeKind.ADVANCED_SATELLITE));
        return recipe().map(recipe -> List.of(pipe.forTable(new CraftingSetup(recipe, bySatellite(), priority, byproduct, fluids))))
                .orElse(List.of());
    }

    // which ingredients go to which satellite: one of its own per slot with the upgrade, else the bottom row to one
    private Map<String, List<ItemBatch>> bySatellite() {
        if (pipe.upgradeCount(UpgradeKind.ADVANCED_SATELLITE) == 0) {
            return Map.of(satelliteName, satelliteInputs());
        }
        Map<String, List<ItemBatch>> inputs = new LinkedHashMap<>();
        for (int slot = 0; slot < PipeBlockEntity.OUTPUT_SLOT; slot++) {
            if (!slotSatellites[slot].isEmpty() && !config.getItem(slot).isEmpty()) {
                inputs.computeIfAbsent(slotSatellites[slot], key -> new ArrayList<>()).add(marked(slot));
            }
        }
        return inputs;
    }

    private static Set<ItemType> plainTypes(Container container, int from, int to) {
        Set<ItemType> types = new HashSet<>();
        for (int slot = from; slot < to; slot++) {
            if (!container.getItem(slot).isEmpty()) {
                types.add(McItemType.batchOf(container.getItem(slot)).type().plain());
            }
        }
        return types;
    }

    // what a crafting pipe clears out
    Optional<Cleanup> cleanup() {
        int listed = pipe.upgradeCount(UpgradeKind.CRAFTING_CLEANUP) * PipeConfigMenu.CLEANUP_PER_UPGRADE;
        return listed == 0 ? Optional.empty() : Optional.of(new Cleanup(
                plainTypes(config, PipeBlockEntity.CLEANUP_FIRST, PipeBlockEntity.CLEANUP_FIRST + listed), exclude));
    }

    // what a supplier pipe keeps up
    List<Supply> supplies() {
        return pipe.hasUpgrade(UpgradeKind.PATTERN) ? List.of() : List.of(new Supply(supplyTargets(), supplyMode()));
    }

    // what a supplier pipe keeps in which slot
    List<PatternSupply> patternSupplies() {
        if (!pipe.hasUpgrade(UpgradeKind.PATTERN)) {
            return List.of();
        }
        List<PatternSupply.Entry> entries = new ArrayList<>();
        for (int slot = 0; slot < PipeBlockEntity.OUTPUT_SLOT; slot++) {
            if (!config.getItem(slot).isEmpty()) {
                entries.add(new PatternSupply.Entry(slotSettings.patternSlot(slot), McItemType.batchOf(config.getItem(slot))));
            }
        }
        return List.of(new PatternSupply(entries, supplyMode(), slotSettings.limited()));
    }

    // how a basic pipe takes items nobody asked for
    List<SinkRule> sinkRules() {
        ItemSink sink = new ItemSink(supplyTargets().keySet(), defaultRoute);
        // a basic pipe with nothing listed and no default route takes nothing
        List<SinkRule> rules = sink.filter().isEmpty() && !sink.defaultRoute() ? List.of() : List.of(sink);
        List<FuzzyType> fuzzy = pipe.hasUpgrade(UpgradeKind.FUZZY)
                ? slotSettings.fuzzyTypes(config.getItems().subList(0, PipeBlockEntity.OUTPUT_SLOT)) : List.of();
        if (!fuzzy.isEmpty()) {
            rules = List.of(sink, new FuzzySink(fuzzy));
        }
        return rules;
    }

    // what a void pipe destroys when nobody else has room for it, everything while nothing is listed
    List<SinkRule> voidRules() {
        return List.of(new VoidSink(supplyTargets().keySet()));
    }

    Optional<Recipe> recipe() {
        ItemStack output = config.getItem(PipeBlockEntity.OUTPUT_SLOT);
        List<ItemBatch> inputs = new ArrayList<>();
        for (int slot = 0; slot < PipeBlockEntity.OUTPUT_SLOT; slot++) {
            if (!config.getItem(slot).isEmpty()) {
                inputs.add(marked(slot));
            }
        }
        if (output.isEmpty() || inputs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Recipe(inputs, marked(PipeBlockEntity.OUTPUT_SLOT)));
    }

    // with a satellite chosen the bottom row of the recipe goes there, the rest to this pipe
    List<ItemBatch> satelliteInputs() {
        List<ItemBatch> inputs = new ArrayList<>();
        for (int slot = PipeBlockEntity.SATELLITE_ROW; slot < PipeBlockEntity.OUTPUT_SLOT && !satelliteName.isEmpty(); slot++) {
            if (!config.getItem(slot).isEmpty()) {
                inputs.add(marked(slot));
            }
        }
        return inputs;
    }

    Map<ItemType, Integer> supplyTargets() {
        Map<ItemType, Integer> targets = new LinkedHashMap<>();
        for (int slot = 0; slot < PipeBlockEntity.OUTPUT_SLOT; slot++) {
            ItemStack stack = config.getItem(slot);
            if (!stack.isEmpty()) {
                targets.merge(McItemType.batchOf(stack).type(), stack.getCount(), Integer::sum);
            }
        }
        return targets;
    }
}
