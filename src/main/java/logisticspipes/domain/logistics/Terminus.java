package logisticspipes.domain.logistics;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import logisticspipes.domain.item.ItemType;

// overflow for listed items: wanted more than a default route, less than any item sink
public record Terminus(Set<ItemType> filter) implements SinkRule {

    public static final int PRIORITY = 40;

    public Terminus {
        filter = Set.copyOf(filter);
    }

    @Override
    public OptionalInt priority(ItemType type, Map<ItemType, Integer> contents) {
        return filter.contains(type) ? OptionalInt.of(PRIORITY) : OptionalInt.empty();
    }

    @Override
    public int energy() {
        return 4;
    }

    @Override
    public Set<ItemType> wants() {
        return filter;
    }
}
