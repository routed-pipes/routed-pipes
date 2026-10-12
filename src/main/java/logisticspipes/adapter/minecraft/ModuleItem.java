package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.Set;
import java.util.function.Supplier;

import logisticspipes.adapter.minecraft.menu.NamesMenu;
import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.domain.crafting.Cleanup;
import logisticspipes.domain.crafting.CraftingSetup;
import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.EnchantmentSink;
import logisticspipes.domain.logistics.Extraction;
import logisticspipes.domain.logistics.ExtractionMode;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.ModuleKind;
import logisticspipes.domain.logistics.PassiveSupplier;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.PolymorphicItemSink;
import logisticspipes.domain.logistics.ProviderMode;
import logisticspipes.domain.logistics.ProviderSettings;
import logisticspipes.domain.logistics.SinkRule;
import logisticspipes.domain.logistics.Supply;
import logisticspipes.domain.logistics.SupplyMode;
import logisticspipes.domain.logistics.Terminus;
import logisticspipes.domain.logistics.TraitSink;
import logisticspipes.domain.logistics.UpgradeKind;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

// a module for chassis pipes, its settings live on the item so they survive being moved around
public class ModuleItem extends Item {

    private static final int FILTER_SLOTS = PipeBlockEntity.CONFIG_SLOTS;
    private static final String DEFAULT_ROUTE_TAG = "LogisticsPipesDefaultRoute";
    private static final String MODE_TAG = "LogisticsPipesMode";
    private static final String EXCLUDE_TAG = "LogisticsPipesExclude";
    private static final String NAMES_TAG = "LogisticsPipesNames";
    private static final String SETTINGS_TAG = "Settings";
    private static final String SATELLITE_TAG = "LogisticsPipesSatellite";
    private static final String PRIORITY_TAG = "LogisticsPipesPriority";

    private final ModuleKind kind;
    private final PipeKind settingsLayout;
    private final Supplier<MenuType<PipeConfigMenu>> settingsMenu;
    private final Supplier<MenuType<NamesMenu>> namesMenu;

    // modules without settings get no layout and no menu
    public ModuleItem(Properties properties, ModuleKind kind, PipeKind settingsLayout,
            Supplier<MenuType<PipeConfigMenu>> settingsMenu, Supplier<MenuType<NamesMenu>> namesMenu) {
        super(properties.stacksTo(1));
        this.kind = kind;
        this.settingsLayout = settingsLayout;
        this.settingsMenu = settingsMenu;
        this.namesMenu = namesMenu;
    }

    public ModuleKind kind() {
        return kind;
    }

    // which settings screen a kind of module uses: with or without the default route switch
    public static Optional<PipeKind> settingsLayoutFor(ModuleKind kind) {
        return switch (kind) {
            case ITEM_SINK -> Optional.of(PipeKind.BASIC);
            case TERMINUS, PASSIVE_SUPPLIER, ENCHANTMENT_SINK_MK2, ACTIVE_SUPPLIER -> Optional.of(PipeKind.SUPPLIER);
            case PROVIDER, ADVANCED_EXTRACTOR -> Optional.of(PipeKind.PROVIDER);
            case CRAFTER -> Optional.of(PipeKind.CRAFTING);
            default -> Optional.empty();
        };
    }

    public static boolean listsNames(ModuleKind kind) {
        return kind == ModuleKind.MOD_ITEM_SINK || kind == ModuleKind.TAG_ITEM_SINK || kind == ModuleKind.CREATIVE_TAB_ITEM_SINK;
    }

    // what an item sink module takes
    public static ItemSink sinkOf(ItemStack module) {
        return new ItemSink(filterTypes(module).keySet(), defaultRouteOf(module));
    }

    // how a module wants unrequested items, empty for modules that do not take any
    public static Optional<SinkRule> ruleOf(ItemStack module) {
        if (!(module.getItem() instanceof ModuleItem item)) {
            return Optional.empty();
        }
        return switch (item.kind) {
            case ITEM_SINK -> Optional.of(sinkOf(module));
            case TERMINUS -> Optional.of(new Terminus(filterTypes(module).keySet()));
            case PASSIVE_SUPPLIER -> Optional.of(new PassiveSupplier(filterTypes(module)));
            case POLYMORPHIC_ITEM_SINK -> Optional.of(new PolymorphicItemSink());
            case MOD_ITEM_SINK -> Optional.of(new TraitSink(TraitSink.Trait.MOD, Set.copyOf(namesOf(module))));
            case TAG_ITEM_SINK -> Optional.of(new TraitSink(TraitSink.Trait.TAG, Set.copyOf(namesOf(module))));
            case CREATIVE_TAB_ITEM_SINK -> Optional.of(new TraitSink(TraitSink.Trait.GROUP, Set.copyOf(namesOf(module))));
            case ENCHANTMENT_SINK -> Optional.of(EnchantmentSink.any());
            case ENCHANTMENT_SINK_MK2 -> Optional.of(EnchantmentSink.of(plainTypes(module)));
            default -> Optional.empty();
        };
    }

    public static Optional<Extraction> extractionOf(ItemStack module) {
        if (!(module.getItem() instanceof ModuleItem item)) {
            return Optional.empty();
        }
        return switch (item.kind) {
            case EXTRACTOR -> Optional.of(Extraction.of(ExtractionMode.EXTRACTOR));
            case QUICK_SORT -> Optional.of(Extraction.of(ExtractionMode.QUICK_SORT));
            case ADVANCED_EXTRACTOR -> Optional.of(new Extraction(ExtractionMode.EXTRACTOR, plainTypes(module),
                    intOf(module, EXCLUDE_TAG) != 0));
            default -> Optional.empty();
        };
    }

    // listed items with their marker counts added up
    private static Map<ItemType, Integer> filterTypes(ItemStack module) {
        Map<ItemType, Integer> types = new LinkedHashMap<>();
        for (ItemStack stack : filterOf(module)) {
            if (!stack.isEmpty()) {
                types.merge(McItemType.batchOf(stack).type(), stack.getCount(), Integer::sum);
            }
        }
        return types;
    }

    // the listed items whatever their damage or data
    private static Set<ItemType> plainTypes(ItemStack module) {
        Set<ItemType> types = new HashSet<>();
        filterTypes(module).keySet().forEach(type -> types.add(type.plain()));
        return types;
    }

    public static List<String> namesOf(ItemStack module) {
        List<String> names = new ArrayList<>();
        ListTag list = module.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getList(NAMES_TAG, Tag.TAG_STRING);
        for (int index = 0; index < list.size(); index++) {
            names.add(list.getString(index));
        }
        return names;
    }

    public static void setNames(ItemStack module, List<String> names) {
        ListTag list = new ListTag();
        names.forEach(name -> list.add(StringTag.valueOf(name)));
        CustomData.update(DataComponents.CUSTOM_DATA, module, tag -> tag.put(NAMES_TAG, list));
    }

    public static void configureFilter(ItemStack module, List<ItemStack> filter, boolean exclude) {
        configureSink(module, filter, false);
        CustomData.update(DataComponents.CUSTOM_DATA, module, tag -> tag.putInt(EXCLUDE_TAG, exclude ? 1 : 0));
    }

    public static NonNullList<ItemStack> filterOf(ItemStack module) {
        NonNullList<ItemStack> filter = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
        module.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(filter);
        return filter;
    }

    public static boolean defaultRouteOf(ItemStack module) {
        return module.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(DEFAULT_ROUTE_TAG);
    }

    private static int intOf(ItemStack module, String tag) {
        return module.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(tag);
    }

    public boolean hasSettings() {
        return namesMenu != null || settingsLayout != null;
    }

    // the mode a module is in, -1 for modules without modes
    private static int modeOf(ItemStack module) {
        ModuleKind kind = ((ModuleItem) module.getItem()).kind;
        if (kind != ModuleKind.PROVIDER && kind != ModuleKind.ACTIVE_SUPPLIER) {
            return -1;
        }
        boolean set = module.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(MODE_TAG);
        return set || kind == ModuleKind.PROVIDER ? intOf(module, MODE_TAG) : SupplyMode.BULK50.ordinal();
    }

    // what a crafter module makes, empty until it has ingredients and a result
    public static Optional<CraftingSetup> craftingOf(ItemStack module) {
        return craftingOf(module, false);
    }

    // what an idle crafter module clears out of its machine, with that many cleanup upgrades
    public static Optional<Cleanup> cleanupOf(ItemStack module, int upgrades) {
        if (upgrades == 0 || !(module.getItem() instanceof ModuleItem item) || item.kind != ModuleKind.CRAFTER) {
            return Optional.empty();
        }
        NonNullList<ItemStack> slots = filterOf(module);
        Set<ItemType> listed = new HashSet<>();
        int last = PipeBlockEntity.CLEANUP_FIRST + Math.min(PipeBlockEntity.CLEANUP_SLOTS, upgrades * PipeConfigMenu.CLEANUP_PER_UPGRADE);
        for (int slot = PipeBlockEntity.CLEANUP_FIRST; slot < last; slot++) {
            if (!slots.get(slot).isEmpty()) {
                listed.add(McItemType.batchOf(slots.get(slot)).type().plain());
            }
        }
        return Optional.of(new Cleanup(listed, excludes(module)));
    }

    // a crafter keeps what is on its cleanup list unless told otherwise, everything else lists what to take
    private static boolean excludes(ItemStack module) {
        boolean set = module.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(EXCLUDE_TAG);
        return set ? intOf(module, EXCLUDE_TAG) != 0 : ((ModuleItem) module.getItem()).kind == ModuleKind.CRAFTER;
    }

    public static Optional<CraftingSetup> craftingOf(ItemStack module, boolean withByproduct) {
        if (!(module.getItem() instanceof ModuleItem item) || item.kind != ModuleKind.CRAFTER) {
            return Optional.empty();
        }
        NonNullList<ItemStack> slots = filterOf(module);
        int satellite = intOf(module, SATELLITE_TAG);
        List<ItemBatch> inputs = new ArrayList<>();
        List<ItemBatch> forSatellite = new ArrayList<>();
        for (int slot = 0; slot < PipeBlockEntity.OUTPUT_SLOT; slot++) {
            if (!slots.get(slot).isEmpty()) {
                inputs.add(McItemType.batchOf(slots.get(slot)));
                if (satellite != 0 && slot >= PipeBlockEntity.SATELLITE_ROW) {
                    forSatellite.add(McItemType.batchOf(slots.get(slot)));
                }
            }
        }
        ItemStack output = slots.get(PipeBlockEntity.OUTPUT_SLOT);
        if (inputs.isEmpty() || output.isEmpty()) {
            return Optional.empty();
        }
        ItemStack extra = slots.get(PipeBlockEntity.BYPRODUCT_SLOT);
        return Optional.of(new CraftingSetup(new Recipe(inputs, McItemType.batchOf(output)), forSatellite, satellite,
                intOf(module, PRIORITY_TAG), withByproduct && !extra.isEmpty() ? Optional.of(McItemType.batchOf(extra)) : Optional.empty()));
    }

    public static void configureCrafter(ItemStack module, List<ItemStack> inputs, ItemStack output, int satellite, int priority) {
        List<ItemStack> slots = new ArrayList<>(inputs);
        while (slots.size() < PipeBlockEntity.OUTPUT_SLOT) {
            slots.add(ItemStack.EMPTY);
        }
        slots.add(output);
        configureSink(module, slots, false);
        CustomData.update(DataComponents.CUSTOM_DATA, module, tag -> {
            tag.putInt(SATELLITE_TAG, satellite);
            tag.putInt(PRIORITY_TAG, priority);
        });
    }

    public static Optional<Supply> supplyOf(ItemStack module) {
        if (!(module.getItem() instanceof ModuleItem item) || item.kind != ModuleKind.ACTIVE_SUPPLIER) {
            return Optional.empty();
        }
        return Optional.of(new Supply(filterTypes(module), SupplyMode.values()[Math.floorMod(modeOf(module), SupplyMode.values().length)]));
    }

    public static void configureSupplier(ItemStack module, List<ItemStack> targets, SupplyMode mode) {
        configureSink(module, targets, false);
        CustomData.update(DataComponents.CUSTOM_DATA, module, tag -> tag.putInt(MODE_TAG, mode.ordinal()));
    }

    // what a provider module hands out
    public static ProviderSettings providerOf(ItemStack module) {
        ProviderMode[] modes = ProviderMode.values();
        return new ProviderSettings(modes[Math.floorMod(intOf(module, MODE_TAG), modes.length)], filterTypes(module).keySet(),
                intOf(module, EXCLUDE_TAG) != 0);
    }

    public static void configureProvider(ItemStack module, List<ItemStack> filter, ProviderMode mode, boolean exclude) {
        configureSink(module, filter, false);
        CustomData.update(DataComponents.CUSTOM_DATA, module, tag -> {
            tag.putInt(MODE_TAG, mode.ordinal());
            tag.putInt(EXCLUDE_TAG, exclude ? 1 : 0);
        });
    }

    public static void configureSink(ItemStack module, List<ItemStack> filter, boolean defaultRoute) {
        List<ItemStack> slots = new ArrayList<>(filter);
        while (slots.size() < FILTER_SLOTS) {
            slots.add(ItemStack.EMPTY);
        }
        module.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots.subList(0, FILTER_SLOTS)));
        CustomData.update(DataComponents.CUSTOM_DATA, module, tag -> tag.putBoolean(DEFAULT_ROUTE_TAG, defaultRoute));
    }

    // what was set up on a module, for carrying it over to another module of the same kind
    public static CompoundTag saveSettings(ItemStack module, HolderLookup.Provider registries) {
        CompoundTag tag = ContainerHelper.saveAllItems(new CompoundTag(), filterOf(module), registries);
        // modes, names, satellite and the rest all live in the custom data
        tag.put(SETTINGS_TAG, module.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
        return tag;
    }

    public static void loadSettings(ItemStack module, CompoundTag settings, HolderLookup.Provider registries) {
        NonNullList<ItemStack> filter = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(settings, filter, registries);
        module.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(filter));
        CustomData.set(DataComponents.CUSTOM_DATA, module, settings.getCompound(SETTINGS_TAG));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".description").withStyle(ChatFormatting.GRAY));
    }

    // modules with settings are set up by using them in the hand
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack module = player.getItemInHand(hand);
        if (!hasSettings()) {
            return InteractionResultHolder.pass(module);
        }
        if (!level.isClientSide) {
            // nothing to import a recipe from while the module is in the hand
            settingsProvider(module, holder -> !module.isEmpty() && holder.getInventory().contains(module), () -> { },
                    target -> { }, upgrade -> 0).ifPresent(player::openMenu);
        }
        return InteractionResultHolder.sidedSuccess(module, level.isClientSide);
    }

    // the settings screen of a module wherever it is, changed runs after every change
    // upgrades says how many of an upgrade the module has where it sits
    public Optional<MenuProvider> settingsProvider(ItemStack module, Predicate<Player> usable, Runnable changed,
            Consumer<Container> importRecipe, ToIntFunction<UpgradeKind> upgrades) {
        if (namesMenu != null) {
            return Optional.of(new SimpleMenuProvider((id, inventory, user) -> new NamesMenu(namesMenu.get(), id, inventory,
                    module, usable, changed), module.getHoverName()));
        }
        if (settingsLayout == null) {
            return Optional.empty();
        }
        return Optional.of(new SimpleMenuProvider((id, inventory, user) -> settings(id, user, module, usable, changed,
                importRecipe, upgrades), module.getHoverName()));
    }

    // the same screen as a pipe with that layout, every change is written straight back to the module
    private PipeConfigMenu settings(int id, Player player, ItemStack module, Predicate<Player> usable, Runnable changed,
            Consumer<Container> importRecipe, ToIntFunction<UpgradeKind> upgrades) {
        SimpleContainer filter = new SimpleContainer(PipeBlockEntity.CONFIG_SLOTS);
        NonNullList<ItemStack> stored = filterOf(module);
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            filter.setItem(slot, stored.get(slot));
        }
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case PipeConfigMenu.DATA_DEFAULT_ROUTE -> defaultRouteOf(module) ? 1 : 0;
                    case PipeConfigMenu.DATA_MODE -> modeOf(module);
                    case PipeConfigMenu.DATA_EXCLUDE -> excludes(module) ? 1 : 0;
                    case PipeConfigMenu.DATA_BYPRODUCT -> upgrades.applyAsInt(UpgradeKind.CRAFTING_BYPRODUCT);
                    case PipeConfigMenu.DATA_CLEANUP -> upgrades.applyAsInt(UpgradeKind.CRAFTING_CLEANUP);
                    case PipeConfigMenu.DATA_SATELLITE -> intOf(module, SATELLITE_TAG);
                    case PipeConfigMenu.DATA_PRIORITY -> intOf(module, PRIORITY_TAG);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == PipeConfigMenu.DATA_DEFAULT_ROUTE) {
                    configureSink(module, filterOf(module), value != 0);
                } else if (index != PipeConfigMenu.DATA_BYPRODUCT && index != PipeConfigMenu.DATA_CLEANUP) {
                    String tag = switch (index) {
                        case PipeConfigMenu.DATA_MODE -> MODE_TAG;
                        case PipeConfigMenu.DATA_EXCLUDE -> EXCLUDE_TAG;
                        case PipeConfigMenu.DATA_SATELLITE -> SATELLITE_TAG;
                        default -> PRIORITY_TAG;
                    };
                    CustomData.update(DataComponents.CUSTOM_DATA, module, data -> data.putInt(tag, value));
                }
                changed.run();
            }

            @Override
            public int getCount() {
                return PipeConfigMenu.DATA_COUNT;
            }
        };
        filter.addListener(container -> {
            List<ItemStack> slots = new ArrayList<>();
            for (int slot = 0; slot < FILTER_SLOTS; slot++) {
                slots.add(filter.getItem(slot).copy());
            }
            configureSink(module, slots, defaultRouteOf(module));
            changed.run();
        });
        return new PipeConfigMenu(settingsMenu.get(), id, player.getInventory(), settingsLayout, filter, data, usable,
                () -> importRecipe.accept(filter));
    }
}
