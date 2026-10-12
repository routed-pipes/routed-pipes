package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.domain.item.ItemType;
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
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
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

    private final ModuleKind kind;
    private final PipeKind settingsLayout;
    private final Supplier<MenuType<PipeConfigMenu>> settingsMenu;

    // modules without settings get no layout and no menu
    public ModuleItem(Properties properties, ModuleKind kind, PipeKind settingsLayout,
            Supplier<MenuType<PipeConfigMenu>> settingsMenu) {
        super(properties.stacksTo(1));
        this.kind = kind;
        this.settingsLayout = settingsLayout;
        this.settingsMenu = settingsMenu;
    }

    public ModuleKind kind() {
        return kind;
    }

    // which settings screen a kind of module uses: with or without the default route switch
    public static Optional<PipeKind> settingsLayoutFor(ModuleKind kind) {
        return switch (kind) {
            case ITEM_SINK -> Optional.of(PipeKind.BASIC);
            case TERMINUS, PASSIVE_SUPPLIER -> Optional.of(PipeKind.SUPPLIER);
            case PROVIDER -> Optional.of(PipeKind.PROVIDER);
            default -> Optional.empty();
        };
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
            default -> Optional.empty();
        };
    }

    public static Optional<ExtractionMode> extractionOf(ItemStack module) {
        if (!(module.getItem() instanceof ModuleItem item)) {
            return Optional.empty();
        }
        return switch (item.kind) {
            case EXTRACTOR -> Optional.of(ExtractionMode.EXTRACTOR);
            case QUICK_SORT -> Optional.of(ExtractionMode.QUICK_SORT);
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
