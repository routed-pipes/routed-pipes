package routedpipes.adapter.minecraft;

import java.util.Map;
import java.util.function.Supplier;

import routedpipes.adapter.minecraft.menu.ChannelMenu;
import routedpipes.adapter.minecraft.menu.ChassisMenu;
import routedpipes.adapter.minecraft.menu.FirewallMenu;
import routedpipes.adapter.minecraft.menu.FrequencyCardMenu;
import routedpipes.adapter.minecraft.menu.PipeConfigMenu;
import routedpipes.adapter.minecraft.menu.RequestMenu;
import routedpipes.adapter.minecraft.menu.RequestTableMenu;
import routedpipes.adapter.minecraft.menu.UpgradeMenu;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;

// everything the pipes need from the loader, handed in when the blocks are created
public record PipeServices(
        Supplier<BlockEntityType<PipeBlockEntity>> blockEntityType,
        ItemStorage storage,
        LevelNetworks networks,
        Supplier<MenuType<RequestMenu>> requestMenu,
        Supplier<MenuType<ChassisMenu>> chassisMenu,
        Supplier<MenuType<UpgradeMenu>> upgradeMenu,
        Supplier<MenuType<FirewallMenu>> firewallMenu,
        Supplier<MenuType<RequestTableMenu>> requestTableMenu,
        Map<PipeKind, Supplier<MenuType<PipeConfigMenu>>> configMenus,
        FluidServices fluids,
        DiskMenus diskMenus,
        EnergyAccess energy,
        Supplier<MenuType<ChannelMenu>> connectorMenu,
        Supplier<MenuType<FrequencyCardMenu>> frequencyCardMenu) {

    public boolean hasSettings(PipeKind kind) {
        return configMenus.containsKey(kind);
    }

    public MenuType<PipeConfigMenu> configMenu(PipeKind kind) {
        return configMenus.get(kind).get();
    }
}
