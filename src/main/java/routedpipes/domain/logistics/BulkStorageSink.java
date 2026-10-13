package routedpipes.domain.logistics;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import routedpipes.domain.item.ItemType;

// one of these in a network turns every default route of it into a store: next to taking what nobody else wants
// it takes what its inventory already holds, as keenly as if the item were on its list
// the module itself takes nothing, its network does the work
public record BulkStorageSink() implements SinkRule {

    // how a store bids for an item it holds, like a polymorphic sink would
    public static final SinkBid STORED = new SinkBid(ItemSink.FILTER_PRIORITY, new PolymorphicItemSink().energy(), 0);

    // whether a pipe with these rules is a store: a default route and nothing but plain lists
    public static boolean stores(List<SinkRule> rules) {
        return rules.stream().allMatch(rule -> rule instanceof ItemSink)
                && rules.stream().anyMatch(rule -> rule instanceof ItemSink sink && sink.defaultRoute());
    }

    @Override
    public OptionalInt priority(ItemType type, Map<ItemType, Integer> contents) {
        return OptionalInt.empty();
    }

    @Override
    public int energy() {
        return 0;
    }

    @Override
    public Set<ItemType> wants() {
        return Set.of();
    }
}
