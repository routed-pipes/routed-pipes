package logisticspipes.adapter.minecraft;

import java.util.function.Supplier;

import logisticspipes.adapter.minecraft.menu.StatisticsTableMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;

// what the statistics table needs from the loader
public record StatisticsTableServices(
        Supplier<BlockEntityType<StatisticsTableBlockEntity>> blockEntityType,
        Supplier<MenuType<StatisticsTableMenu>> menu) {
}
