package logisticspipes.adapter.minecraft;

import java.util.function.Supplier;

import net.minecraft.world.level.block.entity.BlockEntityType;

// everything the tubes need from the loader, handed in when the blocks are created
public record TubeServices(
        Supplier<BlockEntityType<TubeBlockEntity>> blockEntityType,
        Supplier<BlockEntityType<TubePartBlockEntity>> partBlockEntityType,
        Supplier<TubePartBlock> part,
        LevelNetworks networks) {
}
