package logisticspipes.adapter.minecraft;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// access to inventories in the world, every loader has its own api for this
public interface ItemStorage {

    boolean exists(Level level, BlockPos pos, Direction side);

    // returns what did not fit
    ItemStack insert(Level level, BlockPos pos, Direction side, ItemStack stack, boolean simulate);

    // copies of everything that can be taken out through this side
    List<ItemStack> contents(Level level, BlockPos pos, Direction side);

    // takes out up to count items that match the given stack
    ItemStack extract(Level level, BlockPos pos, Direction side, ItemStack match, int count);
}
