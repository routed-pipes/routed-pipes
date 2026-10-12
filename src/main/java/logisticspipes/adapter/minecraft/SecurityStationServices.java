package logisticspipes.adapter.minecraft;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import logisticspipes.adapter.minecraft.menu.ChannelMenu;
import logisticspipes.adapter.minecraft.menu.SecurityStationMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;

// what the security station needs from the loader
public record SecurityStationServices(
        Supplier<BlockEntityType<SecurityStationBlockEntity>> blockEntityType,
        Supplier<MenuType<SecurityStationMenu>> menu,
        Supplier<SecurityCardItem> card,
        Supplier<MenuType<ChannelMenu>> channelMenu,
        // whether the game has computers at all, their settings are only shown then
        BooleanSupplier computers) {
}
