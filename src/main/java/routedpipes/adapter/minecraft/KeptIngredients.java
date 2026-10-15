package routedpipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import routedpipes.domain.crafting.CraftingSetup;
import routedpipes.domain.crafting.Recipe;
import routedpipes.domain.item.FuzzyFlags;
import routedpipes.domain.item.FuzzyType;
import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.item.ItemType;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;

// ingredients that a craft hands back, like a flashed programmer or a tool that wears down a little
// they are needed once and not per craft, a worn one does as well as a new one
public final class KeptIngredients {

    private static final FuzzyFlags ANY_WEAR = new FuzzyFlags(false, true, false, false);

    private KeptIngredients() {
    }

    public static List<ItemStack> of(CraftingRecipe recipe, CraftingInput input) {
        NonNullList<ItemStack> remaining = recipe.getRemainingItems(input);
        List<ItemStack> kept = new ArrayList<>();
        for (int slot = 0; slot < remaining.size(); slot++) {
            ItemStack ingredient = input.getItem(slot);
            if (!ingredient.isEmpty() && remaining.get(slot).is(ingredient.getItem())) {
                kept.add(ingredient.copyWithCount(1));
            }
        }
        return kept;
    }

    // the same item however worn
    static boolean matches(ItemStack stack, ItemStack kept) {
        return !stack.isEmpty() && !kept.isEmpty() && undamaged(stack).equals(undamaged(kept));
    }

    private static ItemType undamaged(ItemStack stack) {
        return McItemType.batchOf(stack).type().undamaged();
    }

    // what the network is asked for, any wear will do
    static ItemType type(ItemStack kept) {
        return new FuzzyType(undamaged(kept), ANY_WEAR);
    }

    static int count(Container container, ItemStack kept) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (matches(container.getItem(slot), kept)) {
                count += container.getItem(slot).getCount();
            }
        }
        return count;
    }

    // the stacks without one match for each kept ingredient, empty stacks are left out
    static List<ItemStack> without(List<ItemStack> stacks, List<ItemStack> kept) {
        List<ItemStack> left = new ArrayList<>(kept);
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && !removeOne(left, stack)) {
                rest.add(stack);
            }
        }
        return rest;
    }

    private static boolean removeOne(List<ItemStack> stacks, ItemStack like) {
        for (int index = 0; index < stacks.size(); index++) {
            if (matches(stacks.get(index), like)) {
                stacks.remove(index);
                return true;
            }
        }
        return false;
    }

    // crafts once with the 3x3 grid of markers out of the container, false and nothing touched if something is missing
    // the stacks themselves go into the craft, so a tool wears down and what comes back goes to leftover
    public static boolean craft(CraftingRecipe recipe, List<ItemStack> grid, Container from, Consumer<ItemStack> leftover) {
        List<ItemStack> kept = of(recipe, CraftingInput.of(3, 3, grid));
        List<ItemStack> keptLeft = new ArrayList<>(kept);
        int[] taken = new int[grid.size()];
        int[] left = new int[from.getContainerSize()];
        for (int slot = 0; slot < left.length; slot++) {
            left[slot] = from.getItem(slot).getCount();
        }
        for (int index = 0; index < grid.size(); index++) {
            ItemStack marker = grid.get(index);
            taken[index] = -1;
            if (marker.isEmpty()) {
                continue;
            }
            boolean worn = removeOne(keptLeft, marker);
            for (int slot = 0; slot < left.length && taken[index] < 0; slot++) {
                ItemStack stack = from.getItem(slot);
                if (left[slot] > 0 && (worn ? matches(stack, marker) : ItemStack.isSameItemSameComponents(stack, marker))) {
                    taken[index] = slot;
                    left[slot]--;
                }
            }
            if (taken[index] < 0) {
                return false;
            }
        }
        List<ItemStack> used = new ArrayList<>();
        for (int index = 0; index < grid.size(); index++) {
            used.add(taken[index] < 0 ? ItemStack.EMPTY : from.getItem(taken[index]).copyWithCount(1));
        }
        NonNullList<ItemStack> remaining = recipe.getRemainingItems(CraftingInput.of(3, 3, used));
        for (int index = 0; index < grid.size(); index++) {
            if (taken[index] >= 0) {
                from.removeItem(taken[index], 1);
            }
        }
        // a programmer comes back as it was, a tool a bit worn, an empty bucket instead of a full one
        remaining.forEach(stack -> {
            if (!stack.isEmpty()) {
                leftover.accept(stack);
            }
        });
        return true;
    }

    // what a crafting pipe or module in front of the table crafts, what the table hands back is needed once
    // and the setup says how many of those the table has already
    static CraftingSetup forTable(CraftingSetup setup, CraftingTableBlockEntity table) {
        List<ItemStack> kept = new ArrayList<>(table.kept());
        if (kept.isEmpty()) {
            return setup;
        }
        Recipe recipe = setup.recipe();
        List<ItemBatch> inputs = new ArrayList<>();
        List<ItemBatch> keptInputs = new ArrayList<>(recipe.kept());
        Map<ItemType, Integer> held = new LinkedHashMap<>(setup.held());
        for (ItemBatch input : recipe.inputs()) {
            ItemStack stack = McItemType.stackOf(input);
            if (removeOne(kept, stack)) {
                keptInputs.add(new ItemBatch(type(stack), 1));
                held.put(type(stack), count(table.storage(), stack));
            } else {
                inputs.add(input);
            }
        }
        return setup.withKept(new Recipe(inputs, recipe.output(), keptInputs), held);
    }
}
