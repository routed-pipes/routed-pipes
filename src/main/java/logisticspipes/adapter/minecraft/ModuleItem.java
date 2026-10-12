package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.ModuleKind;
import logisticspipes.domain.logistics.PipeKind;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

// a module for chassis pipes, its settings live on the item so they survive being moved around
public class ModuleItem extends Item {

    private static final int FILTER_SLOTS = 9;
    private static final String DEFAULT_ROUTE_TAG = "LogisticsPipesDefaultRoute";

    private final ModuleKind kind;
    private final Supplier<MenuType<PipeConfigMenu>> settingsMenu;

    public ModuleItem(Properties properties, ModuleKind kind, Supplier<MenuType<PipeConfigMenu>> settingsMenu) {
        super(properties.stacksTo(1));
        this.kind = kind;
        this.settingsMenu = settingsMenu;
    }

    public ModuleKind kind() {
        return kind;
    }

    // what an item sink module takes
    public static ItemSink sinkOf(ItemStack module) {
        Set<ItemType> filter = new HashSet<>();
        for (ItemStack stack : filterOf(module)) {
            if (!stack.isEmpty()) {
                filter.add(McItemType.batchOf(stack).type());
            }
        }
        return new ItemSink(filter, defaultRouteOf(module));
    }

    public static NonNullList<ItemStack> filterOf(ItemStack module) {
        NonNullList<ItemStack> filter = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
        module.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(filter);
        return filter;
    }

    public static boolean defaultRouteOf(ItemStack module) {
        return module.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(DEFAULT_ROUTE_TAG);
    }

    public static void configureSink(ItemStack module, List<ItemStack> filter, boolean defaultRoute) {
        List<ItemStack> slots = new ArrayList<>(filter);
        while (slots.size() < FILTER_SLOTS) {
            slots.add(ItemStack.EMPTY);
        }
        module.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots.subList(0, FILTER_SLOTS)));
        CustomData.update(DataComponents.CUSTOM_DATA, module, tag -> tag.putBoolean(DEFAULT_ROUTE_TAG, defaultRoute));
    }

    // item sink modules are set up by using them in the hand
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack module = player.getItemInHand(hand);
        if (kind != ModuleKind.ITEM_SINK) {
            return InteractionResultHolder.pass(module);
        }
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider((id, inventory, user) -> settings(id, user, module), module.getHoverName()));
        }
        return InteractionResultHolder.sidedSuccess(module, level.isClientSide);
    }

    // the same screen as a basic pipe, every change is written straight back to the module
    private PipeConfigMenu settings(int id, Player player, ItemStack module) {
        SimpleContainer filter = new SimpleContainer(PipeBlockEntity.CONFIG_SLOTS);
        NonNullList<ItemStack> stored = filterOf(module);
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            filter.setItem(slot, stored.get(slot));
        }
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return index == PipeConfigMenu.DATA_DEFAULT_ROUTE && defaultRouteOf(module) ? 1 : 0;
            }

            @Override
            public void set(int index, int value) {
                if (index == PipeConfigMenu.DATA_DEFAULT_ROUTE) {
                    configureSink(module, filterOf(module), value != 0);
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
        return new PipeConfigMenu(settingsMenu.get(), id, player.getInventory(), PipeKind.BASIC, filter, data,
                user -> !module.isEmpty() && user.getInventory().contains(module), () -> { });
    }
}
