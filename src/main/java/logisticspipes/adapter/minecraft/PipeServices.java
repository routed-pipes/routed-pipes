package logisticspipes.adapter.minecraft;

import java.util.function.Supplier;

import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.adapter.minecraft.menu.RequestMenu;
import logisticspipes.domain.logistics.PipeKind;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;

// everything the pipes need from the loader, handed in when the blocks are created
public record PipeServices(
        Supplier<BlockEntityType<PipeBlockEntity>> blockEntityType,
        ItemStorage storage,
        LevelNetworks networks,
        Supplier<MenuType<RequestMenu>> requestMenu,
        Supplier<MenuType<PipeConfigMenu>> supplierMenu,
        Supplier<MenuType<PipeConfigMenu>> craftingMenu,
        Supplier<MenuType<PipeConfigMenu>> satelliteMenu) {

    public MenuType<PipeConfigMenu> configMenu(PipeKind kind) {
        return switch (kind) {
            case SUPPLIER -> supplierMenu.get();
            case CRAFTING -> craftingMenu.get();
            case SATELLITE -> satelliteMenu.get();
            default -> throw new IllegalArgumentException("no settings for " + kind);
        };
    }
}
