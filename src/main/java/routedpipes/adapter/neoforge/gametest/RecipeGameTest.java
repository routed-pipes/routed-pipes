package routedpipes.adapter.neoforge.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.netty.buffer.Unpooled;
import routedpipes.adapter.minecraft.ProgrammedRecipe;
import routedpipes.adapter.minecraft.ProgrammerItem;
import routedpipes.adapter.neoforge.RoutedPipes;
import routedpipes.domain.program.ProgramCatalog;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(RoutedPipes.MODID)
@PrefixGameTestTemplate(false)
public class RecipeGameTest {

    private static final String EMPTY = "empty";

    static Item item(String name) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(RoutedPipes.MODID, name));
    }

    // what a crafting grid filled row by row makes, an empty stack when nothing
    static ItemStack crafted(GameTestHelper helper, int width, int height, Item... grid) {
        return crafted(helper, input(width, height, Arrays.stream(grid)
                .map(item -> item == null ? ItemStack.EMPTY : new ItemStack(item)).toArray(ItemStack[]::new)));
    }

    private static CraftingInput input(int width, int height, ItemStack... grid) {
        return CraftingInput.of(width, height, List.of(grid));
    }

    private static Optional<RecipeHolder<CraftingRecipe>> recipe(GameTestHelper helper, CraftingInput input) {
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
    }

    private static ItemStack crafted(GameTestHelper helper, CraftingInput input) {
        return recipe(helper, input).map(holder -> holder.value().assemble(input, helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    private static ItemStack programmer(String flashedFor) {
        ItemStack programmer = new ItemStack(RoutedPipes.LOGISTICS_PROGRAMMER.get());
        if (flashedFor != null) {
            ProgrammerItem.flash(programmer, ProgramCatalog.byId(flashedFor).orElseThrow());
        }
        return programmer;
    }

    // the grid of a request pipe, a provider pipe is made of the same things with another program
    private static CraftingInput requestPipeGrid(ItemStack programmer) {
        ItemStack redstone = new ItemStack(Items.REDSTONE);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        return input(3, 3,
                ItemStack.EMPTY, programmer, ItemStack.EMPTY,
                redstone, new ItemStack(item("chip_basic")), redstone,
                iron, new ItemStack(item("basic_logistics_pipe")), iron);
    }

    @GameTest(template = EMPTY)
    public static void aProgrammedRecipeTakesOnlyTheProgrammerFlashedForItsResult(GameTestHelper helper) {
        helper.assertTrue(crafted(helper, requestPipeGrid(programmer("request_pipe"))).is(item("request_pipe")),
                "the programmer for the request pipe makes a request pipe");
        helper.assertTrue(crafted(helper, requestPipeGrid(programmer("provider_pipe"))).is(item("provider_pipe")),
                "the one for the provider pipe a provider pipe");
        helper.assertTrue(crafted(helper, requestPipeGrid(programmer(null))).isEmpty(), "a blank programmer makes nothing");
        helper.assertTrue(crafted(helper, requestPipeGrid(programmer("crafting_pipe"))).isEmpty(),
                "a programmer for something else makes nothing");
        helper.assertTrue(crafted(helper, requestPipeGrid(ItemStack.EMPTY)).isEmpty(), "and neither does none at all");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theProgrammerStaysInTheGridWithItsProgram(GameTestHelper helper) {
        CraftingInput input = requestPipeGrid(programmer("request_pipe"));

        NonNullList<ItemStack> remaining = recipe(helper, input).orElseThrow().value().getRemainingItems(input);

        for (int slot = 0; slot < remaining.size(); slot++) {
            if (slot == 1) {
                helper.assertTrue(ItemStack.isSameItemSameComponents(remaining.get(slot), programmer("request_pipe"))
                        && remaining.get(slot).getCount() == 1, "the flashed programmer is left, got " + remaining.get(slot));
            } else {
                helper.assertTrue(remaining.get(slot).isEmpty(), "everything else is used up");
            }
        }
        helper.succeed();
    }

    // what a client gets sent has to work like the recipe of the server
    @GameTest(template = EMPTY)
    public static void aProgrammedRecipeSurvivesTheWayToTheClient(GameTestHelper helper) {
        CraftingInput input = requestPipeGrid(programmer("request_pipe"));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());

        Recipe.STREAM_CODEC.encode(buffer, recipe(helper, input).orElseThrow().value());
        Recipe<?> received = Recipe.STREAM_CODEC.decode(buffer);

        helper.assertTrue(received instanceof ProgrammedRecipe, "it is still a programmed recipe");
        ProgrammedRecipe recipe = (ProgrammedRecipe) received;
        helper.assertTrue(recipe.matches(input, helper.getLevel()), "it takes the flashed programmer");
        helper.assertTrue(!recipe.matches(requestPipeGrid(programmer(null)), helper.getLevel()), "and no blank one");
        helper.assertTrue(recipe.assemble(input, helper.getLevel().registryAccess()).is(item("request_pipe")), "and makes the pipe");
        helper.succeed();
    }

    // what the compiler offers and what the recipes ask for come from two tables
    @GameTest(template = EMPTY)
    public static void everyProgramHasARecipeThatAsksForIt(GameTestHelper helper) {
        Set<Item> asked = new HashSet<>();
        helper.getLevel().getRecipeManager().getRecipes().forEach(holder -> {
            if (holder.value() instanceof ProgrammedRecipe recipe) {
                Item made = recipe.getResultItem(helper.getLevel().registryAccess()).getItem();
                helper.assertTrue(asked.add(made), "two programmed recipes for " + made);
                helper.assertTrue(recipe.getIngredients().stream().anyMatch(ingredient ->
                        Arrays.stream(ingredient.getItems()).anyMatch(stack -> ProgrammerItem.flashedFor(stack, made))),
                        "the recipe for " + made + " should show the programmer flashed for it");
            }
        });
        Set<Item> offered = new HashSet<>();
        ProgramCatalog.all().forEach(program -> offered.add(ProgrammerItem.itemOf(program)));
        helper.assertTrue(asked.equals(offered), "programs and programmed recipes differ: " + asked + " against " + offered);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void routedPipesAreTurnedBackIntoBasicPipes(GameTestHelper helper) {
        helper.assertTrue(crafted(helper, 1, 1, item("request_pipe_mk2")).is(item("basic_logistics_pipe")), "a request pipe mk2");
        helper.assertTrue(crafted(helper, 1, 1, item("chassis_pipe_mk5")).is(item("basic_logistics_pipe")), "a chassis pipe");
        helper.assertTrue(crafted(helper, 1, 1, item("fluid_supplier_pipe")).is(item("fluid_basic_pipe")), "a fluid supplier pipe");
        helper.assertTrue(crafted(helper, 1, 1, item("basic_logistics_pipe")).isEmpty()
                && crafted(helper, 1, 1, item("fluid_basic_pipe")).isEmpty()
                && crafted(helper, 1, 1, item("request_table")).isEmpty()
                && crafted(helper, 1, 1, item("transport_pipe")).isEmpty(), "basic pipes, the table and plain pipes stay");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aModuleOnItsOwnLosesItsSettings(GameTestHelper helper) {
        ItemStack module = new ItemStack(item("module_mod_item_sink"));
        CustomData.update(DataComponents.CUSTOM_DATA, module, tag -> tag.putInt("Anything", 1));

        ItemStack reset = crafted(helper, input(1, 1, module));

        helper.assertTrue(reset.is(module.getItem()) && reset.getCount() == 1, "the same module comes out");
        helper.assertTrue(reset.getComponentsPatch().isEmpty(), "with nothing set on it");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void pipesStartFromGlassAndIron(GameTestHelper helper) {
        ItemStack pipes = crafted(helper, 3, 3, Items.IRON_INGOT, Items.REDSTONE, Items.IRON_INGOT, Items.GLASS, null, Items.GLASS,
                Items.IRON_INGOT, Items.REDSTONE, Items.IRON_INGOT);
        helper.assertTrue(pipes.is(item("transport_pipe")) && pipes.getCount() == 8, "eight transport pipes");
        helper.assertTrue(crafted(helper, 1, 2, item("chip_fpga"), item("transport_pipe")).is(item("basic_logistics_pipe")),
                "an fpga on a transport pipe makes a basic logistics pipe");
        ItemStack upgrades = crafted(helper, input(3, 3,
                new ItemStack(Items.REDSTONE), programmer("upgrade_disconnection"), new ItemStack(Items.REDSTONE),
                new ItemStack(Items.IRON_INGOT), new ItemStack(item("chip_basic")), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.PAPER), new ItemStack(Items.GOLD_NUGGET), new ItemStack(Items.PAPER)));
        helper.assertTrue(upgrades.is(item("upgrade_disconnection")) && upgrades.getCount() == 8, "eight disconnection upgrades");
        helper.succeed();
    }

    // a tag that does not exist would leave a recipe nobody can craft
    @GameTest(template = EMPTY)
    public static void everyIngredientCanBeHad(GameTestHelper helper) {
        List<String> broken = new ArrayList<>();
        helper.getLevel().getRecipeManager().getRecipes().forEach(recipe -> {
            if (recipe.id().getNamespace().equals(RoutedPipes.MODID) && recipe.value().getIngredients().stream()
                    .anyMatch(ingredient -> !ingredient.isEmpty() && ingredient.getItems().length == 0)) {
                broken.add(recipe.id().getPath());
            }
        });
        helper.assertTrue(broken.isEmpty(), "ingredients without an item in " + broken);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void rawChipsAreCraftedAndSmelted(GameTestHelper helper) {
        ItemStack raw = crafted(helper, 2, 2, Items.SAND, Items.COPPER_INGOT, Items.COPPER_INGOT, Items.SAND);
        helper.assertTrue(raw.is(item("chip_basic_raw")) && raw.getCount() == 2, "sand and copper make two raw basic chips");
        // the game has copper, so gold is not taken for it
        helper.assertTrue(crafted(helper, 2, 2, Items.SAND, Items.GOLD_INGOT, Items.GOLD_INGOT, Items.SAND).isEmpty(),
                "the recipe with gold is for games without copper");
        ItemStack advanced = crafted(helper, 2, 2, item("chip_basic_raw"), Items.DIAMOND, Items.DIAMOND, item("chip_basic_raw"));
        helper.assertTrue(advanced.is(item("chip_advanced_raw")) && advanced.getCount() == 2, "two raw advanced chips");
        for (String chip : List.of("chip_basic", "chip_advanced", "chip_fpga")) {
            ItemStack smelted = helper.getLevel().getRecipeManager()
                    .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(item(chip + "_raw"))), helper.getLevel())
                    .map(holder -> holder.value().getResultItem(helper.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
            helper.assertTrue(smelted.is(item(chip)), chip + " comes out of the furnace");
        }
        helper.succeed();
    }
}
