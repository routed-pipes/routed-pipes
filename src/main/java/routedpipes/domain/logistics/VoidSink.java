package routedpipes.domain.logistics;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import routedpipes.domain.item.ItemType;

// what a void pipe takes in: wanted less than any default route, so only what nobody else has room for
// with nothing listed that is everything, otherwise only the listed items
public record VoidSink(Set<ItemType> filter) implements SinkRule {

    public static final int PRIORITY = -10;

    public VoidSink {
        filter = Set.copyOf(filter);
    }

    @Override
    public OptionalInt priority(ItemType type, Map<ItemType, Integer> contents) {
        return filter.isEmpty() || filter.contains(type) ? OptionalInt.of(PRIORITY) : OptionalInt.empty();
    }

    @Override
    public int energy() {
        return 2;
    }

    @Override
    public Set<ItemType> wants() {
        return filter;
    }

    @Override
    public boolean takesUnlisted() {
        return filter.isEmpty();
    }
}
