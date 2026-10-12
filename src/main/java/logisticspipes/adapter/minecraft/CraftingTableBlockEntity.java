package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import logisticspipes.adapter.minecraft.menu.CraftingTableMenu;
import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class CraftingTableBlockEntity extends BlockEntity {

    public static final int PATTERN_SLOTS = 9;
    public static final int STORAGE_SLOTS = 18;

    // the recipe grid, markers only
    private final SimpleContainer pattern = new SimpleContainer(PATTERN_SLOTS);
    private final SimpleContainer storage = new SimpleContainer(STORAGE_SLOTS);
    // crafted items nobody has taken yet, a craft can make more than was asked for
    private final SimpleContainer surplus = new SimpleContainer(1);
    private RecipeHolder<CraftingRecipe> recipe;
    private boolean recipeKnown;

    public CraftingTableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        pattern.addListener(container -> {
            recipeKnown = false;
            setChanged();
        });
        storage.addListener(container -> setChanged());
        surplus.addListener(container -> setChanged());
    }

    public Container pattern() {
        return pattern;
    }

    public Container storage() {
        return storage;
    }

    public Container surplus() {
        return surplus;
    }

    MenuProvider menuProvider() {
        CraftingTableBlock block = (CraftingTableBlock) getBlockState().getBlock();
        return new SimpleMenuProvider((id, inventory, player) -> new CraftingTableMenu(block.services().menu().get(), id,
                inventory, this), getBlockState().getBlock().getName());
    }

    private CraftingInput input() {
        List<ItemStack> grid = new ArrayList<>();
        for (int slot = 0; slot < PATTERN_SLOTS; slot++) {
            grid.add(pattern.getItem(slot).copyWithCount(1));
        }
        return CraftingInput.of(3, 3, grid);
    }

    private Optional<RecipeHolder<CraftingRecipe>> recipe() {
        if (!recipeKnown && level != null) {
            recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input(), level).orElse(null);
            recipeKnown = true;
        }
        return Optional.ofNullable(recipe);
    }

    // what one craft of the recipe grid makes, empty if the grid is not a recipe
    public ItemStack result() {
        return recipe().map(found -> found.value().assemble(input(), level.registryAccess())).orElse(ItemStack.EMPTY);
    }

    private int runsPossible() {
        List<ItemBatch> inputs = new ArrayList<>();
        for (int slot = 0; slot < PATTERN_SLOTS; slot++) {
            if (!pattern.getItem(slot).isEmpty()) {
                inputs.add(McItemType.batchOf(pattern.getItem(slot).copyWithCount(1)));
            }
        }
        ItemStack result = result();
        if (result.isEmpty() || inputs.isEmpty()) {
            return 0;
        }
        Map<ItemType, Integer> stock = new LinkedHashMap<>();
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            ItemStack stack = storage.getItem(slot);
            if (!stack.isEmpty()) {
                stock.merge(McItemType.batchOf(stack).type(), stack.getCount(), Integer::sum);
            }
        }
        return new Recipe(inputs, McItemType.batchOf(result)).runsPossible(stock);
    }

    // what could be taken out right now, capped at one stack
    public ItemStack available() {
        ItemStack waiting = surplus.getItem(0);
        ItemStack result = result();
        if (!waiting.isEmpty() && !ItemStack.isSameItemSameComponents(waiting, result)) {
            return waiting.copy();
        }
        if (result.isEmpty()) {
            return ItemStack.EMPTY;
        }
        long total = waiting.getCount() + (long) runsPossible() * result.getCount();
        return total == 0 ? ItemStack.EMPTY : result.copyWithCount((int) Math.min(total, result.getMaxStackSize()));
    }

    // takes up to amount of the result, crafting as often as needed
    public ItemStack takeResult(int amount, boolean simulate) {
        ItemStack available = available();
        if (available.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int taken = Math.min(amount, available.getCount());
        if (simulate) {
            return available.copyWithCount(taken);
        }
        int ready = surplus.getItem(0).getCount();
        ItemStack result = result();
        while (ready < taken) {
            craftOnce();
            ready += result.getCount();
        }
        surplus.setItem(0, ready == taken ? ItemStack.EMPTY : available.copyWithCount(ready - taken));
        return available.copyWithCount(taken);
    }

    private void craftOnce() {
        CraftingInput input = input();
        NonNullList<ItemStack> remaining = recipe().orElseThrow().value().getRemainingItems(input);
        for (int slot = 0; slot < PATTERN_SLOTS; slot++) {
            ItemStack needed = pattern.getItem(slot);
            if (!needed.isEmpty()) {
                consume(needed);
            }
        }
        // things like empty buckets go back into storage
        for (ItemStack left : remaining) {
            if (!left.isEmpty()) {
                ItemStack rest = storage.addItem(left);
                if (!rest.isEmpty()) {
                    Block.popResource(level, worldPosition, rest);
                }
            }
        }
    }

    private void consume(ItemStack needed) {
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            if (ItemStack.isSameItemSameComponents(storage.getItem(slot), needed)) {
                storage.removeItem(slot, 1);
                return;
            }
        }
        throw new IllegalStateException("ingredient vanished while crafting: " + needed);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Pattern", save(pattern, registries));
        tag.put("Storage", save(storage, registries));
        tag.put("Surplus", save(surplus, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        load(pattern, tag.getCompound("Pattern"), registries);
        load(storage, tag.getCompound("Storage"), registries);
        load(surplus, tag.getCompound("Surplus"), registries);
    }

    private static CompoundTag save(SimpleContainer container, HolderLookup.Provider registries) {
        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < items.size(); slot++) {
            items.set(slot, container.getItem(slot));
        }
        return ContainerHelper.saveAllItems(new CompoundTag(), items, registries);
    }

    private static void load(SimpleContainer container, CompoundTag tag, HolderLookup.Provider registries) {
        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        for (int slot = 0; slot < items.size(); slot++) {
            container.setItem(slot, items.get(slot));
        }
    }
}
