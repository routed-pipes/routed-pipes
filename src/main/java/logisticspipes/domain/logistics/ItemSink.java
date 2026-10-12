package logisticspipes.domain.logistics;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import logisticspipes.domain.item.ItemType;

// what a basic pipe takes in: listed items first, and as default route anything nobody else wants
public record ItemSink(Set<ItemType> filter, boolean defaultRoute) implements SinkRule {

    public static final int FILTER_PRIORITY = 2;
    public static final int DEFAULT_ROUTE_PRIORITY = 0;

    public ItemSink {
        filter = Set.copyOf(filter);
    }

    // empty when the item is not wanted here
    public OptionalInt priority(ItemType type) {
        if (filter.contains(type)) {
            return OptionalInt.of(FILTER_PRIORITY);
        }
        return defaultRoute ? OptionalInt.of(DEFAULT_ROUTE_PRIORITY) : OptionalInt.empty();
    }

    @Override
    public OptionalInt priority(ItemType type, Map<ItemType, Integer> contents) {
        return priority(type);
    }
}
