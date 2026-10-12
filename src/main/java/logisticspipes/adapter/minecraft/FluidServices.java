package logisticspipes.adapter.minecraft;

import java.util.Map;
import java.util.function.Supplier;

import logisticspipes.adapter.minecraft.menu.FluidConfigMenu;
import logisticspipes.adapter.minecraft.menu.FluidRequestMenu;
import logisticspipes.domain.logistics.PipeKind;
import net.minecraft.world.inventory.MenuType;

// what the fluid pipes need from the loader
public record FluidServices(
        FluidStorage storage,
        Map<PipeKind, Supplier<MenuType<FluidConfigMenu>>> configMenus,
        Supplier<MenuType<FluidRequestMenu>> requestMenu) {

    public boolean hasSettings(PipeKind kind) {
        return configMenus.containsKey(kind);
    }

    public MenuType<FluidConfigMenu> configMenu(PipeKind kind) {
        return configMenus.get(kind).get();
    }
}
