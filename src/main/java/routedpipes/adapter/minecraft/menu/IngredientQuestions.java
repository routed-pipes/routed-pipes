package routedpipes.adapter.minecraft.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

import routedpipes.adapter.minecraft.McItemType;
import routedpipes.domain.item.ItemType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

// client side of asking whether the network can bring the ingredients of a recipe to the open table
// only the server knows the network, so the answers come in a little later and are kept per recipe
// a recipe viewer looks at them every tick for every recipe it shows
public final class IngredientQuestions {

    public interface Asker {
        void ask(int containerId, int question, List<List<ItemStack>> choices);
    }

    // what the server said about the ingredients of a recipe and when
    public record Answer(int network, List<ItemStack> shortfall, long at) {
    }

    // an answer older than this is asked for again, and so is a question that got no answer for this long
    public static final long REFRESH_MILLIS = 1000;
    // the menu sends the number back as a short
    private static final int MAX_QUESTION = 30000;

    private final Asker asker;
    private final LongSupplier clock;
    private final Map<List<List<ItemType>>, Answer> answers = new HashMap<>();
    private int containerId = -1;
    // the one question the server has not answered yet
    private List<List<ItemType>> asking;
    private int question;
    private long askedAt;

    public IngredientQuestions(Asker asker, LongSupplier clock) {
        this.asker = asker;
        this.clock = clock;
    }

    // the last thing the server said about these ingredients, empty until it has answered
    // every slot comes with what it takes, the server picks among them
    // asks the server when there is nothing fresh, one question at a time
    public <M extends AbstractContainerMenu & IngredientMenu> Optional<Answer> answer(M menu, List<List<ItemStack>> choices) {
        long now = clock.getAsLong();
        if (menu.containerId != containerId) {
            containerId = menu.containerId;
            forget();
        }
        if (asking != null && menu.answeredQuestion() == question) {
            answers.put(asking, new Answer(menu.network(), menu.shortfall(), now));
            asking = null;
        }
        List<List<ItemType>> key = new ArrayList<>();
        for (List<ItemStack> slot : choices) {
            key.add(slot.stream().map(stack -> McItemType.batchOf(stack).type()).toList());
        }
        Answer known = answers.get(key);
        boolean stale = known == null || now - known.at() >= REFRESH_MILLIS;
        if (stale && (asking == null || now - askedAt >= REFRESH_MILLIS)) {
            asking = key;
            question = question % MAX_QUESTION + 1;
            askedAt = now;
            asker.ask(containerId, question, choices);
        }
        return Optional.ofNullable(known);
    }

    // after something was ordered the old answers no longer hold
    public void forget() {
        answers.clear();
        asking = null;
    }
}
