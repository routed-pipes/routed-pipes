package routedpipes.adapter.minecraft.menu;

import java.util.List;

import net.minecraft.world.item.ItemStack;

// a menu a recipe viewer may ask whether the network can bring the ingredients of a recipe, see IngredientQuestions
// the answer comes back through the menu, marked with the number of the question
public interface IngredientMenu {

    // no pipe can order
    int NETWORK_NONE = 0;
    // the network can bring everything that is not there yet
    int NETWORK_SUPPLIES = 1;
    int NETWORK_MISSING = 2;

    // nothing is ordered
    void askIngredients(int question, List<ItemStack> ingredients);

    // the number of the question the menu holds the answer to
    int answeredQuestion();

    int network();

    // what the network cannot bring, see Shortfall
    List<ItemStack> shortfall();
}
