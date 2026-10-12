package logisticspipes.adapter.minecraft;

import java.util.function.Supplier;

import logisticspipes.adapter.minecraft.menu.DiskMenu;
import logisticspipes.adapter.minecraft.menu.MacroEditMenu;
import net.minecraft.world.inventory.MenuType;

// the screens for the macros on a disk, handed in by the loader
public record DiskMenus(Supplier<MenuType<DiskMenu>> disk, Supplier<MenuType<MacroEditMenu>> editor) {
}
