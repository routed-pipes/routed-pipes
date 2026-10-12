package logisticspipes.domain.request;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import logisticspipes.domain.item.ItemType;

// turns types with fuzzy flags into the real items that stand in for them
// for a type without flags every answer is the type itself
public final class FuzzyMatch {

    private FuzzyMatch() {
    }

    // the kinds of items in stock that will do for the wanted one, the item of the slot itself first
    public static List<ItemType> candidates(ItemType wanted, Supplier<? extends Collection<ItemType>> stocked) {
        if (wanted.exact() == wanted) {
            return List.of(wanted);
        }
        List<ItemType> candidates = new ArrayList<>();
        candidates.add(wanted.exact());
        for (ItemType type : stocked.get()) {
            if (!candidates.contains(type) && wanted.accepts(type)) {
                candidates.add(type);
            }
        }
        return candidates;
    }

    // whether a crafter with that result can be asked for what is wanted, flags on either side count
    public static boolean makes(ItemType result, ItemType wanted) {
        return result.accepts(wanted) || wanted.accepts(result);
    }

    // what is taken out of the machine for an order: of everything that will do the kind there is most of
    public static ItemType largest(ItemType ordered, Map<ItemType, Integer> contents) {
        ItemType best = ordered.exact();
        if (best == ordered) {
            return ordered;
        }
        for (Map.Entry<ItemType, Integer> held : contents.entrySet()) {
            if (ordered.accepts(held.getKey()) && held.getValue() > contents.getOrDefault(best, 0)) {
                best = held.getKey();
            }
        }
        return best;
    }

    // amounts by the item players see, a slot with flags counts as the item in it
    public static Map<ItemType, Integer> exact(Map<ItemType, Integer> amounts) {
        Map<ItemType, Integer> exact = new LinkedHashMap<>();
        amounts.forEach((type, count) -> exact.merge(type.exact(), count, Integer::sum));
        return exact;
    }
}
