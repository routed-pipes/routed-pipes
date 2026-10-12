package logisticspipes.adapter.minecraft;

import java.util.function.Supplier;

import logisticspipes.adapter.minecraft.menu.ProgramCompilerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;

// what the program compiler needs from the loader
public record ProgramCompilerServices(
        Supplier<BlockEntityType<ProgramCompilerBlockEntity>> blockEntityType,
        Supplier<MenuType<ProgramCompilerMenu>> menu) {
}
