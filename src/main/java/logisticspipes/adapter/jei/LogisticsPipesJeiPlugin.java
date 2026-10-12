package logisticspipes.adapter.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.client.ClientMenuLink;
import logisticspipes.adapter.minecraft.client.CraftingTableScreen;
import logisticspipes.adapter.minecraft.client.MarkerScreen;
import logisticspipes.adapter.minecraft.client.PipeConfigScreen;
import logisticspipes.adapter.minecraft.client.RecipeViewerCheck;
import logisticspipes.adapter.minecraft.menu.CraftingTableMenu;
import logisticspipes.adapter.minecraft.menu.IngredientQuestions;
import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.domain.logistics.PipeKind;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

// only loaded when JEI is installed: drag items onto marker slots, move recipes into tables and crafting pipes,
// and an info page for every item of the mod
@JeiPlugin
public class LogisticsPipesJeiPlugin implements IModPlugin {

    private static final String NAMESPACE = "logisticspipes";

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, "jei");
    }

    // every marker slot of the screen takes the dragged item
    private static final class MarkerTargets<T extends Screen & MarkerScreen> implements IGhostIngredientHandler<T> {

        @Override
        public <I> List<Target<I>> getTargetsTyped(T screen, ITypedIngredient<I> ingredient, boolean doStart) {
            Optional<ItemStack> dragged = ingredient.getItemStack();
            List<Target<I>> targets = new ArrayList<>();
            if (dragged.isEmpty() || !(screen.markers() instanceof AbstractContainerMenu menu)) {
                return targets;
            }
            for (int index = 0; index < screen.markers().markerSlots(); index++) {
                Slot slot = menu.getSlot(index);
                if (!slot.isActive()) {
                    continue;
                }
                int markerSlot = index;
                Rect2i area = new Rect2i(screen.left() + slot.x, screen.top() + slot.y, 16, 16);
                targets.add(new Target<>() {
                    @Override
                    public Rect2i getArea() {
                        return area;
                    }

                    @Override
                    public void accept(I ignored) {
                        ClientMenuLink.setMarker(screen.containerId(), markerSlot, dragged.get().copyWithCount(1));
                    }
                });
            }
            return targets;
        }

        @Override
        public void onComplete() {
        }
    }

    // the plus button on a crafting recipe fills the grid of an open logistics crafting table and has the network
    // bring the ingredients its storage lacks, hovering the button tells which of them the network cannot bring
    private static final class TableTransfer implements IRecipeTransferHandler<CraftingTableMenu, RecipeHolder<CraftingRecipe>> {

        // the button still sets the grid, nothing is ordered until the network has everything
        private record NetworkShortfall(List<ItemStack> shortfall) implements IRecipeTransferError {

            @Override
            public Type getType() {
                return Type.COSMETIC;
            }

            @Override
            public void getTooltip(ITooltipBuilder tooltip) {
                tooltip.add(Component.translatable("logisticspipes.jei.network_cannot_supply").withStyle(ChatFormatting.RED));
                for (ItemStack stack : shortfall) {
                    tooltip.add(Component.translatable("logisticspipes.request.missing_entry",
                            CraftingTableMenu.shortfallCount(stack), stack.getHoverName()));
                }
            }
        }

        private final IngredientQuestions questions = new IngredientQuestions(
                (containerId, question, ingredients) -> ClientMenuLink.askIngredients(containerId, question, ingredients, false),
                Util::getMillis);

        @Override
        public Class<? extends CraftingTableMenu> getContainerClass() {
            return CraftingTableMenu.class;
        }

        @Override
        public Optional<MenuType<CraftingTableMenu>> getMenuType() {
            return Optional.empty();
        }

        @Override
        public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        // JEI calls this every tick for every recipe on screen to find out what its button should say
        @Override
        public IRecipeTransferError transferRecipe(CraftingTableMenu menu, RecipeHolder<CraftingRecipe> recipe,
                IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
            List<IRecipeSlotView> inputs = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
            List<ItemStack> ingredients = new ArrayList<>();
            for (int slot = 0; slot < menu.markerSlots(); slot++) {
                ItemStack stack = slot < inputs.size() ? inputs.get(slot).getDisplayedItemStack().orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
                ingredients.add(stack.copyWithCount(stack.isEmpty() ? 0 : 1));
            }
            if (doTransfer) {
                for (int slot = 0; slot < ingredients.size(); slot++) {
                    ClientMenuLink.setMarker(menu.containerId, slot, ingredients.get(slot));
                }
                // nobody waits for the answer to this one, the next look at the button asks again
                ClientMenuLink.askIngredients(menu.containerId, 0, ingredients, true);
                questions.forget();
                return null;
            }
            return questions.answer(menu, ingredients).filter(answer -> answer.network() == CraftingTableMenu.NETWORK_MISSING)
                    .<IRecipeTransferError>map(answer -> new NetworkShortfall(answer.shortfall())).orElse(null);
        }
    }

    // the plus button on any recipe sets inputs and output of an open crafting pipe, that also covers machines
    private static final class PipeTransfer implements IUniversalRecipeTransferHandler<PipeConfigMenu> {

        private final IRecipeTransferHandlerHelper helper;

        private PipeTransfer(IRecipeTransferHandlerHelper helper) {
            this.helper = helper;
        }

        @Override
        public Class<? extends PipeConfigMenu> getContainerClass() {
            return PipeConfigMenu.class;
        }

        @Override
        public Optional<MenuType<PipeConfigMenu>> getMenuType() {
            return Optional.empty();
        }

        @Override
        public IRecipeTransferError transferRecipe(PipeConfigMenu menu, Object recipe, IRecipeSlotsView recipeSlots,
                Player player, boolean maxTransfer, boolean doTransfer) {
            if (menu.kind() != PipeKind.CRAFTING) {
                return helper.createInternalError();
            }
            List<ItemStack> inputs = merged(recipeSlots.getSlotViews(RecipeIngredientRole.INPUT));
            List<ItemStack> outputs = merged(recipeSlots.getSlotViews(RecipeIngredientRole.OUTPUT));
            if (inputs.isEmpty() || outputs.isEmpty() || inputs.size() > PipeBlockEntity.OUTPUT_SLOT) {
                return helper.createUserErrorWithTooltip(Component.translatable("logisticspipes.jei.recipe_does_not_fit"));
            }
            if (doTransfer) {
                for (int slot = 0; slot < PipeBlockEntity.OUTPUT_SLOT; slot++) {
                    ClientMenuLink.setMarker(menu.containerId, slot, slot < inputs.size() ? inputs.get(slot) : ItemStack.EMPTY);
                }
                ClientMenuLink.setMarker(menu.containerId, PipeBlockEntity.OUTPUT_SLOT, outputs.get(0));
            }
            return null;
        }

        // the shown item of every slot, the same item in several slots added up
        private static List<ItemStack> merged(List<IRecipeSlotView> views) {
            List<ItemStack> stacks = new ArrayList<>();
            for (IRecipeSlotView view : views) {
                ItemStack shown = view.getDisplayedItemStack().orElse(ItemStack.EMPTY);
                if (shown.isEmpty()) {
                    continue;
                }
                Optional<ItemStack> same = stacks.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, shown)).findFirst();
                if (same.isPresent()) {
                    same.get().grow(shown.getCount());
                } else {
                    stacks.add(shown.copy());
                }
            }
            return stacks;
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(PipeConfigScreen.class, new MarkerTargets<>());
        registration.addGhostIngredientHandler(CraftingTableScreen.class, new MarkerTargets<>());
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new TableTransfer(), RecipeTypes.CRAFTING);
        registration.addUniversalRecipeTransferHandler(new PipeTransfer(registration.getTransferHelper()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        Item table = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "logistics_crafting_table"));
        registration.addRecipeCatalyst(new ItemStack(table), RecipeTypes.CRAFTING);
    }

    // the description every item of the mod has becomes its info page
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (Item item : BuiltInRegistries.ITEM) {
            String key = item.getDescriptionId() + ".description";
            if (NAMESPACE.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace()) && I18n.exists(key)) {
                registration.addItemStackInfo(new ItemStack(item), Component.translatable(key));
            }
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        if (System.getProperty(RecipeViewerCheck.ENABLED_PROPERTY) != null) {
            RecipeViewerCheck.setDriver(screen -> {
                try {
                    return drive(runtime, screen);
                } catch (RuntimeException e) {
                    return e.toString();
                }
            });
        }
    }

    // does through JEI what a player does: the plus button on a recipe, and for crafting pipes also a drag onto a slot
    private static String drive(IJeiRuntime runtime, Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return "not a container screen";
        }
        AbstractContainerMenu menu = containerScreen.getMenu();
        Player player = Minecraft.getInstance().player;
        if (menu instanceof CraftingTableMenu) {
            return transfer(runtime, menu, player, RecipeTypes.CRAFTING, "minecraft:stick");
        }
        String problem = transfer(runtime, menu, player, RecipeTypes.SMELTING, "minecraft:iron_ingot_from_smelting_raw_iron");
        if (!problem.isEmpty()) {
            return problem;
        }
        return dragOnto(runtime, screen, new ItemStack(Items.DIAMOND), 2);
    }

    private static <C extends AbstractContainerMenu, R extends Recipe<?>> String transfer(IJeiRuntime runtime, C menu, Player player,
            RecipeType<RecipeHolder<R>> type, String recipeId) {
        Optional<RecipeHolder<R>> recipe = runtime.getRecipeManager().createRecipeLookup(type).get()
                .filter(holder -> holder.id().toString().equals(recipeId)).findFirst();
        if (recipe.isEmpty()) {
            return "JEI does not know the recipe " + recipeId;
        }
        IRecipeCategory<RecipeHolder<R>> category = runtime.getRecipeManager().getRecipeCategory(type);
        Optional<IRecipeTransferHandler<C, RecipeHolder<R>>> handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, category);
        if (handler.isEmpty()) {
            return "JEI found no transfer handler for " + menu.getClass().getSimpleName();
        }
        IRecipeSlotsView slots = runtime.getRecipeManager()
                .createRecipeLayoutDrawableOrShowError(category, recipe.get(), runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup())
                .getRecipeSlotsView();
        IRecipeTransferError error = handler.get().transferRecipe(menu, recipe.get(), slots, player, false, true);
        return error == null ? "" : "transfer refused: " + error.getType();
    }

    private static <T extends Screen> String dragOnto(IJeiRuntime runtime, T screen, ItemStack stack, int target) {
        Optional<IGhostIngredientHandler<T>> handler = runtime.getScreenHelper().getGhostIngredientHandler(screen);
        Optional<ITypedIngredient<ItemStack>> dragged = runtime.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, stack);
        if (handler.isEmpty() || dragged.isEmpty()) {
            return "JEI found no drag handler for " + screen.getClass().getSimpleName();
        }
        List<IGhostIngredientHandler.Target<ItemStack>> targets = handler.get().getTargetsTyped(screen, dragged.get(), true);
        if (targets.size() <= target) {
            return "only " + targets.size() + " drag targets";
        }
        targets.get(target).accept(stack);
        handler.get().onComplete();
        return "";
    }
}
