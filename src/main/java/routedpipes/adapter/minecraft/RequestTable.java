package routedpipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import routedpipes.application.RequestOutcome;
import routedpipes.domain.item.ItemType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

// what a request table has on top of a request pipe: a recipe grid, a buffer that takes what it orders
// and a slot that sends items back into the network
public final class RequestTable {

    public static final int GRID_SLOTS = 9;
    public static final int BUFFER_SLOTS = 27;
    // how long the sort slot waits when nobody takes its item
    private static final int SORT_RETRY = 100;

    private final SimpleContainer grid = new SimpleContainer(GRID_SLOTS);
    private final SimpleContainer buffer = new SimpleContainer(BUFFER_SLOTS);
    private final SimpleContainer sort = new SimpleContainer(1);
    private final PipeBlockEntity pipe;
    private RecipeHolder<CraftingRecipe> recipe;
    private boolean recipeKnown;
    private int sortWait;
    // crafts still to make once their ingredients have arrived
    private int pending;

    RequestTable(PipeBlockEntity pipe) {
        this.pipe = pipe;
        grid.addListener(container -> {
            recipeKnown = false;
            pending = 0;
            pipe.setChanged();
        });
        buffer.addListener(container -> pipe.setChanged());
        sort.addListener(container -> pipe.setChanged());
    }

    public SimpleContainer grid() {
        return grid;
    }

    public SimpleContainer buffer() {
        return buffer;
    }

    public SimpleContainer sort() {
        return sort;
    }

    private CraftingInput input() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < GRID_SLOTS; slot++) {
            stacks.add(grid.getItem(slot).copyWithCount(1));
        }
        return CraftingInput.of(3, 3, stacks);
    }

    private Optional<RecipeHolder<CraftingRecipe>> recipe() {
        Level level = pipe.getLevel();
        if (!recipeKnown && level != null) {
            recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input(), level).orElse(null);
            recipeKnown = true;
        }
        return Optional.ofNullable(recipe);
    }

    // what one craft of the grid makes, empty if the grid is not a recipe
    public ItemStack result() {
        return recipe().map(found -> found.value().assemble(input(), pipe.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
    }

    // the grid as items with counts, for that many crafts
    private Map<ItemType, Integer> ingredients(int crafts) {
        Map<ItemType, Integer> needed = new LinkedHashMap<>();
        for (int slot = 0; slot < GRID_SLOTS; slot++) {
            if (!grid.getItem(slot).isEmpty()) {
                needed.merge(McItemType.batchOf(grid.getItem(slot)).type(), crafts, Integer::sum);
            }
        }
        return needed;
    }

    private Map<ItemType, Integer> stock() {
        Map<ItemType, Integer> stock = new LinkedHashMap<>();
        for (int slot = 0; slot < BUFFER_SLOTS; slot++) {
            if (!buffer.getItem(slot).isEmpty()) {
                stock.merge(McItemType.batchOf(buffer.getItem(slot)).type(), buffer.getItem(slot).getCount(), Integer::sum);
            }
        }
        return stock;
    }

    // crafts once out of the buffer, the result goes into the buffer too, false if something is missing
    public boolean craft() {
        ItemStack result = craftOne();
        pipe.popOut(buffer.addItem(result));
        return !result.isEmpty();
    }

    // crafts once out of the buffer and hands out the result, empty if something is missing
    // leftovers like empty buckets stay in the buffer
    public ItemStack craftOne() {
        ItemStack result = result();
        Map<ItemType, Integer> stock = stock();
        boolean enough = ingredients(1).entrySet().stream().allMatch(entry -> stock.getOrDefault(entry.getKey(), 0) >= entry.getValue());
        if (result.isEmpty() || !enough) {
            return ItemStack.EMPTY;
        }
        NonNullList<ItemStack> remaining = recipe().orElseThrow().value().getRemainingItems(input());
        for (int slot = 0; slot < GRID_SLOTS; slot++) {
            ItemStack needed = grid.getItem(slot);
            for (int from = 0; from < BUFFER_SLOTS && !needed.isEmpty(); from++) {
                if (ItemStack.isSameItemSameComponents(buffer.getItem(from), needed)) {
                    buffer.removeItem(from, 1);
                    break;
                }
            }
        }
        // things like empty buckets
        remaining.forEach(left -> pipe.popOut(buffer.addItem(left)));
        return result;
    }

    // puts a stack into the buffer, what does not fit lands on top of the table
    public void keep(ItemStack stack) {
        pipe.popOut(buffer.addItem(stack));
    }

    // orders the ingredients for that many crafts into the buffer, all of them or nothing
    // with missingOnly what the buffer already holds is not ordered again
    public RequestOutcome orderIngredients(int crafts, boolean missingOnly) {
        return pipe.requestShortfall(ingredients(crafts), missingOnly ? stock() : Map.of(), false);
    }

    // orders what the buffer lacks for that many crafts and makes them as soon as everything is there
    public RequestOutcome orderAndCraft(int crafts) {
        RequestOutcome outcome = orderIngredients(crafts, true);
        if (outcome.started()) {
            pending = crafts;
        }
        return outcome;
    }

    public int pending() {
        return pending;
    }

    // what the network cannot bring for that many crafts beyond what the buffer holds
    public Map<ItemType, Integer> missing(int crafts) {
        if (result().isEmpty()) {
            return Map.of();
        }
        return pipe.requestShortfall(ingredients(crafts), stock(), true).missing();
    }

    // sends everything in the buffer back into the network, what nobody takes stays
    public void storeAll() {
        for (int slot = 0; slot < BUFFER_SLOTS; slot++) {
            if (!buffer.getItem(slot).isEmpty() && pipe.sendOff(buffer.getItem(slot))) {
                buffer.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    // what was ordered arrives here, returns what did not fit
    ItemStack takeIn(ItemStack stack) {
        return buffer.addItem(stack);
    }

    void tick() {
        if (pending > 0 && craft()) {
            pending--;
        }
        ItemStack waiting = sort.getItem(0);
        if (waiting.isEmpty()) {
            sortWait = 0;
        } else if (sortWait > 0) {
            sortWait--;
        } else if (pipe.sendOff(waiting)) {
            sort.setItem(0, ItemStack.EMPTY);
        } else {
            sortWait = SORT_RETRY;
        }
    }

    List<ItemStack> drops() {
        List<ItemStack> drops = new ArrayList<>(buffer.removeAllItems());
        drops.addAll(sort.removeAllItems());
        return drops;
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Grid", ContainerTags.save(grid, registries));
        tag.put("Buffer", ContainerTags.save(buffer, registries));
        tag.put("Sort", ContainerTags.save(sort, registries));
        tag.putInt("Pending", pending);
        return tag;
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        ContainerTags.load(grid, tag.getCompound("Grid"), registries);
        ContainerTags.load(buffer, tag.getCompound("Buffer"), registries);
        ContainerTags.load(sort, tag.getCompound("Sort"), registries);
        pending = tag.getInt("Pending");
    }
}
