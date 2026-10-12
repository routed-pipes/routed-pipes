package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import logisticspipes.adapter.minecraft.menu.NamesMenu;
import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
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
import logisticspipes.domain.logistics.Terminus;
import logisticspipes.domain.logistics.TraitSink;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
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

    private static final int FILTER_SLOTS = 9;
    private static final String DEFAULT_ROUTE_TAG = "LogisticsPipesDefaultRoute";
    private static final String MODE_TAG = "LogisticsPipesMode";
    private static final String EXCLUDE_TAG = "LogisticsPipesExclude";
    private static final String NAMES_TAG = "LogisticsPipesNames";

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
            case TERMINUS, PASSIVE_SUPPLIER, ENCHANTMENT_SINK_MK2 -> Optional.of(PipeKind.SUPPLIER);
            case PROVIDER, ADVANCED_EXTRACTOR -> Optional.of(PipeKind.PROVIDER);
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".description").withStyle(ChatFormatting.GRAY));
    }

    // modules with settings are set up by using them in the hand
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack module = player.getItemInHand(hand);
        if (namesMenu != null) {
            if (!level.isClientSide) {
                player.openMenu(new SimpleMenuProvider((id, inventory, user) -> new NamesMenu(namesMenu.get(), id, inventory,
                        module, holder -> !module.isEmpty() && holder.getInventory().contains(module), () -> { }),
                        module.getHoverName()));
            }
            return InteractionResultHolder.sidedSuccess(module, level.isClientSide);
        }
        if (settingsLayout == null) {
            return InteractionResultHolder.pass(module);
        }
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider((id, inventory, user) -> settings(id, user, module), module.getHoverName()));
        }
        return InteractionResultHolder.sidedSuccess(module, level.isClientSide);
    }

    // the same screen as a pipe with that layout, every change is written straight back to the module
    private PipeConfigMenu settings(int id, Player player, ItemStack module) {
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
                    case PipeConfigMenu.DATA_MODE -> kind == ModuleKind.PROVIDER ? intOf(module, MODE_TAG) : -1;
                    case PipeConfigMenu.DATA_EXCLUDE -> intOf(module, EXCLUDE_TAG);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == PipeConfigMenu.DATA_DEFAULT_ROUTE) {
                    configureSink(module, filterOf(module), value != 0);
                } else if (index == PipeConfigMenu.DATA_MODE || index == PipeConfigMenu.DATA_EXCLUDE) {
                    String tag = index == PipeConfigMenu.DATA_MODE ? MODE_TAG : EXCLUDE_TAG;
                    CustomData.update(DataComponents.CUSTOM_DATA, module, data -> data.putInt(tag, value));
                }
            }

            @Override
            public int getCount() {
                return PipeConfigMenu.DATA_COUNT;
            }
        };
        filter.addListener(changed -> {
            List<ItemStack> slots = new ArrayList<>();
            for (int slot = 0; slot < FILTER_SLOTS; slot++) {
                slots.add(filter.getItem(slot).copy());
            }
            configureSink(module, slots, defaultRouteOf(module));
        });
        return new PipeConfigMenu(settingsMenu.get(), id, player.getInventory(), settingsLayout, filter, data,
                user -> !module.isEmpty() && user.getInventory().contains(module), () -> { });
    }
}
