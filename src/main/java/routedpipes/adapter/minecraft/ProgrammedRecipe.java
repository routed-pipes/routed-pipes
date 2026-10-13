package routedpipes.adapter.minecraft;

import java.util.Arrays;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

// a shaped recipe with a programmer in it: the programmer has to be flashed for what is crafted and stays in the grid
public class ProgrammedRecipe extends ShapedRecipe {

    private static final MapCodec<ProgrammedRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result)).apply(instance, ProgrammedRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, ProgrammedRecipe> STREAM_CODEC = StreamCodec.composite(
            ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.pattern,
            ItemStack.STREAM_CODEC, recipe -> recipe.result,
            ProgrammedRecipe::new);

    // the loader has to register this as a recipe serializer
    public static final RecipeSerializer<ProgrammedRecipe> SERIALIZER = new RecipeSerializer<>() {
        @Override
        public MapCodec<ProgrammedRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ProgrammedRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    };

    private final ItemStack result;
    private NonNullList<Ingredient> shown;

    public ProgrammedRecipe(ShapedRecipePattern pattern, ItemStack result) {
        super("", CraftingBookCategory.MISC, pattern, result);
        this.result = result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return super.matches(input, level) && input.items().stream()
                .allMatch(stack -> !(stack.getItem() instanceof ProgrammerItem) || ProgrammerItem.flashedFor(stack, result.getItem()));
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = super.getRemainingItems(input);
        for (int slot = 0; slot < remaining.size(); slot++) {
            if (input.getItem(slot).getItem() instanceof ProgrammerItem) {
                remaining.set(slot, input.getItem(slot).copyWithCount(1));
            }
        }
        return remaining;
    }

    // recipe viewers show the programmer the way it has to be flashed
    @Override
    public NonNullList<Ingredient> getIngredients() {
        if (shown == null) {
            NonNullList<Ingredient> ingredients = NonNullList.create();
            for (Ingredient ingredient : super.getIngredients()) {
                ingredients.add(flashed(ingredient));
            }
            shown = ingredients;
        }
        return shown;
    }

    private Ingredient flashed(Ingredient ingredient) {
        ItemStack[] stacks = ingredient.getItems();
        if (stacks.length == 0 || !Arrays.stream(stacks).allMatch(stack -> stack.getItem() instanceof ProgrammerItem)) {
            return ingredient;
        }
        ItemStack programmer = stacks[0].copy();
        programmer.set(ProgrammerItem.PROGRAM, result.getItemHolder().unwrapKey().orElseThrow().location());
        return Ingredient.of(programmer);
    }
}
