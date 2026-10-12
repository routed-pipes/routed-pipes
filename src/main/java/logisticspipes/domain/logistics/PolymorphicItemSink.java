package logisticspipes.domain.logistics;

import java.util.Map;
import java.util.OptionalInt;

import logisticspipes.domain.item.ItemType;

// takes whatever kind of item its inventory already holds
public record PolymorphicItemSink() implements SinkRule {

    @Override
    public OptionalInt priority(ItemType type, Map<ItemType, Integer> contents) {
        return contents.getOrDefault(type, 0) > 0 ? OptionalInt.of(ItemSink.FILTER_PRIORITY) : OptionalInt.empty();
    }

    @Override
    public boolean needsContents() {
        return true;
    }

    @Override
    public int energy() {
        return 6;
    }
}
