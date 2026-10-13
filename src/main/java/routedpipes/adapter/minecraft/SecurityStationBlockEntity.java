package routedpipes.adapter.minecraft;

import java.util.UUID;

import routedpipes.adapter.minecraft.menu.ChannelMenu;
import routedpipes.adapter.minecraft.menu.SecurityStationMenu;
import routedpipes.domain.security.SecuritySettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

// the block only knows its id and holds the cards it made, its settings are in SecurityStations
public class SecurityStationBlockEntity extends BlockEntity {

    // making a card costs as much as looking a player up
    public static final int CARD_ENERGY = SecuritySettings.CHECK_ENERGY;
    private static final int MAX_CARDS = 64;
    // the station pays from what is beside it, asked in this order
    private static final Direction[] ENERGY_SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private UUID id = UUID.randomUUID();
    private final SimpleContainer cards = new SimpleContainer(1);

    public SecurityStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        cards.addListener(container -> setChanged());
    }

    public UUID id() {
        return id;
    }

    public Container cards() {
        return cards;
    }

    // server side only
    public SecurityStations.Station station() {
        return SecurityStations.of(level).station(id);
    }

    public boolean hasComputers() {
        return block().services().computers().getAsBoolean();
    }

    private SecurityStationBlock block() {
        return (SecurityStationBlock) getBlockState().getBlock();
    }

    // takes the energy from a routed pipe or a power junction beside the station, the first that has enough pays
    public boolean useEnergy(int amount) {
        for (Direction side : ENERGY_SIDES) {
            BlockPos target = worldPosition.relative(side);
            if (!level.isLoaded(target)) {
                continue;
            }
            BlockEntity neighbour = level.getBlockEntity(target);
            if (neighbour instanceof PipeBlockEntity pipe && pipe.usePower(amount)) {
                return true;
            }
            if (neighbour instanceof PowerJunctionBlockEntity junction && junction.draw(amount, true) == amount) {
                junction.draw(amount, false);
                return true;
            }
        }
        return false;
    }

    public boolean hasRoomForCard() {
        return cards.getItem(0).getCount() < MAX_CARDS;
    }

    // false when there was no room or no energy for another card
    public boolean makeCard() {
        if (!hasRoomForCard() || !useEnergy(CARD_ENERGY)) {
            return false;
        }
        ItemStack present = cards.getItem(0);
        if (present.isEmpty()) {
            cards.setItem(0, block().services().card().get().cardFor(id));
        } else {
            present.grow(1);
            cards.setChanged();
        }
        return true;
    }

    MenuProvider menuProvider() {
        return new SimpleMenuProvider((containerId, inventory, player) -> new SecurityStationMenu(block().services().menu().get(),
                containerId, inventory, this), getBlockState().getBlock().getName());
    }

    // the channels of the world, new ones can be secured with this station
    public MenuProvider channelMenuProvider() {
        return new SimpleMenuProvider((containerId, inventory, player) -> new ChannelMenu(block().services().channelMenu().get(),
                containerId, inventory, null, id, user -> Container.stillValidBlockEntity(this, user)),
                Component.translatable("routedpipes.channel.title"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("Station", id);
        tag.put("Cards", ContainerTags.save(cards, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("Station")) {
            id = tag.getUUID("Station");
        }
        if (tag.contains("Cards")) {
            ContainerTags.load(cards, tag.getCompound("Cards"), registries);
        }
    }
}
