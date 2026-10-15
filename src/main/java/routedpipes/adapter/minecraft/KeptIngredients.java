package routedpipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;

// ingredients that a craft hands back unchanged, like a flashed programmer
final class KeptIngredients {

    private KeptIngredients() {
    }

    static List<ItemStack> of(CraftingRecipe recipe, CraftingInput input) {
        NonNullList<ItemStack> remaining = recipe.getRemainingItems(input);
        List<ItemStack> kept = new ArrayList<>();
        for (int slot = 0; slot < remaining.size(); slot++) {
            ItemStack ingredient = input.getItem(slot);
            if (!ingredient.isEmpty() && ItemStack.isSameItemSameComponents(remaining.get(slot), ingredient)) {
                kept.add(ingredient.copyWithCount(1));
            }
        }
        return kept;
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
            if (ItemStack.isSameItemSameComponents(stacks.get(index), like)) {
                stacks.remove(index);
                return true;
            }
        }
        return false;
    }
}
