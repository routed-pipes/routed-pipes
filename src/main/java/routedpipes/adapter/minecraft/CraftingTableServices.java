package routedpipes.adapter.minecraft;

import java.util.function.Supplier;

import routedpipes.adapter.minecraft.menu.CraftingTableMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;

// what the crafting table needs from the loader
public record CraftingTableServices(
        Supplier<BlockEntityType<CraftingTableBlockEntity>> blockEntityType,
        Supplier<MenuType<CraftingTableMenu>> menu) {
}
