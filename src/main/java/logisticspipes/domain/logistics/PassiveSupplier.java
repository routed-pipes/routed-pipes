package logisticspipes.domain.logistics;

import java.util.Map;
import java.util.OptionalInt;

import logisticspipes.domain.item.ItemType;

// takes passing items until the inventory holds the wanted amount, it never orders anything
public record PassiveSupplier(Map<ItemType, Integer> targets) implements SinkRule {

    public static final int PRIORITY = 2;

    public PassiveSupplier {
        targets = Map.copyOf(targets);
    }

    @Override
    public OptionalInt priority(ItemType type, Map<ItemType, Integer> contents) {
        int wanted = targets.getOrDefault(type, 0);
        return contents.getOrDefault(type, 0) < wanted ? OptionalInt.of(PRIORITY) : OptionalInt.empty();
    }

    @Override
    public boolean needsContents() {
        return true;
    }
}
