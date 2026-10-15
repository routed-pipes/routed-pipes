package routedpipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import routedpipes.adapter.minecraft.menu.CraftingTableMenu;
import routedpipes.application.RequestOutcome;
import routedpipes.domain.crafting.Recipe;
import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.logistics.InventoryRef;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
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
        return new Recipe(inputs, McItemType.batchOf(result)).runsPossible(stock());
    }

    private Map<ItemType, Integer> stock() {
        Map<ItemType, Integer> stock = new LinkedHashMap<>();
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            ItemStack stack = storage.getItem(slot);
            if (!stack.isEmpty()) {
                stock.merge(McItemType.batchOf(stack).type(), stack.getCount(), Integer::sum);
            }
        }
        return stock;
    }

    // what the storage holds and the network can bring, craftable items that are out of stock with 0
    public Map<ItemType, Integer> reachable() {
        Map<ItemType, Integer> reachable = new LinkedHashMap<>(orderingPipe().map(PipeBlockEntity::available).orElse(Map.of()));
        stock().forEach((type, count) -> reachable.merge(type, count, Integer::sum));
        return reachable;
    }

    // the crafting or request pipe that has this table as its inventory, what it orders ends up in the storage
    private Optional<PipeBlockEntity> orderingPipe() {
        InventoryRef self = new InventoryRef(worldPosition.asLong());
        for (Direction side : Direction.values()) {
            BlockPos target = worldPosition.relative(side);
            if (level.isLoaded(target) && level.getBlockEntity(target) instanceof PipeBlockEntity pipe
                    && (pipe.kind() == PipeKind.CRAFTING || pipe.kind().requestPipe())
                    && pipe.inventories().containsValue(self)) {
                return Optional.of(pipe);
            }
        }
        return Optional.empty();
    }

    // what the network could not bring of the ingredients of one craft that are not in storage or on their way
    // nothing is ordered, empty when no pipe next to the table can order
    public Optional<RequestOutcome> askIngredients(List<ItemStack> ingredients) {
        Map<ItemType, Integer> needed = new LinkedHashMap<>();
        for (ItemStack ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                needed.merge(McItemType.batchOf(ingredient).type(), 1, Integer::sum);
            }
        }
        return orderingPipe().map(pipe -> pipe.requestShortfall(needed, stock(), true));
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
        tag.put("Pattern", ContainerTags.save(pattern, registries));
        tag.put("Storage", ContainerTags.save(storage, registries));
        tag.put("Surplus", ContainerTags.save(surplus, registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerTags.load(pattern, tag.getCompound("Pattern"), registries);
        ContainerTags.load(storage, tag.getCompound("Storage"), registries);
        ContainerTags.load(surplus, tag.getCompound("Surplus"), registries);
    }
}
