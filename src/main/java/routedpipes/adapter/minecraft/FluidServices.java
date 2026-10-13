package routedpipes.adapter.minecraft;

import java.util.Map;
import java.util.function.Supplier;

import routedpipes.adapter.minecraft.menu.FluidConfigMenu;
import routedpipes.adapter.minecraft.menu.FluidRequestMenu;
import routedpipes.domain.logistics.PipeKind;
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
