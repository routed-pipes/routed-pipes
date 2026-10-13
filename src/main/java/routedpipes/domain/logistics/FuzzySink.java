package routedpipes.domain.logistics;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import routedpipes.domain.item.FuzzyType;
import routedpipes.domain.item.ItemType;

// the listed items of an item sink that have fuzzy flags: whatever matches one of them is wanted like a listed item
public record FuzzySink(List<FuzzyType> filter) implements SinkRule {

    public FuzzySink {
        filter = List.copyOf(filter);
    }

    @Override
    public OptionalInt priority(ItemType type, Map<ItemType, Integer> contents) {
        for (FuzzyType listed : filter) {
            if (listed.accepts(type)) {
                return OptionalInt.of(ItemSink.FILTER_PRIORITY);
            }
        }
        return OptionalInt.empty();
    }

    @Override
    public int energy() {
        return 2;
    }

    @Override
    public Set<ItemType> wants() {
        return Set.of();
    }

    // it cannot say which items match, so it is asked about every one
    @Override
    public boolean takesUnlisted() {
        return !filter.isEmpty();
    }
}
