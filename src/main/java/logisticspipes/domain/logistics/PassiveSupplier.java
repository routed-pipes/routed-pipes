package logisticspipes.domain.logistics;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import logisticspipes.domain.item.ItemType;

// takes passing items until the inventory holds the wanted amount, it never orders anything
// a stock that is running low matters more than a general item sink, so this ranks highest
public record PassiveSupplier(Map<ItemType, Integer> targets) implements SinkRule {

    public static final int PRIORITY = 3;

    public PassiveSupplier {
        targets = Map.copyOf(targets);
    }

    @Override
    public OptionalInt priority(ItemType type, Map<ItemType, Integer> contents) {
        int wanted = targets.getOrDefault(type, 0);
        return contents.getOrDefault(type, 0) < wanted ? OptionalInt.of(PRIORITY) : OptionalInt.empty();
    }

    // only what is missing up to the target
    @Override
    public int maxItems(ItemType type, Map<ItemType, Integer> contents) {
        return Math.max(0, targets.getOrDefault(type, 0) - contents.getOrDefault(type, 0));
    }

    @Override
    public boolean needsContents() {
        return true;
    }

    @Override
    public int energy() {
        return 4;
    }

    @Override
    public Set<ItemType> wants() {
        return targets.keySet();
    }
}
