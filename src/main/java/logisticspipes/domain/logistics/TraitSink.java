package logisticspipes.domain.logistics;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import logisticspipes.domain.item.ItemType;

// takes every item of the listed mods, tags or creative tabs, wanted less than anything that names the item itself
public record TraitSink(Trait trait, Set<String> names) implements SinkRule {

    public enum Trait {
        MOD(10),
        TAG(20),
        GROUP(10);

        private final int priority;

        Trait(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return priority;
        }

        boolean has(ItemType type, String name) {
            return switch (this) {
                case MOD -> type.namespace().equals(name);
                case TAG -> type.inTag(name);
                case GROUP -> type.inGroup(name);
            };
        }
    }

    public TraitSink {
        names = Set.copyOf(names);
    }

    @Override
    public OptionalInt priority(ItemType type, Map<ItemType, Integer> contents) {
        for (String name : names) {
            if (trait.has(type, name)) {
                return OptionalInt.of(trait.priority);
            }
        }
        return OptionalInt.empty();
    }

    @Override
    public int energy() {
        return 10;
    }

    @Override
    public Set<ItemType> wants() {
        return Set.of();
    }

    // it cannot say which items it wants, so it is asked about every one
    @Override
    public boolean takesUnlisted() {
        return !names.isEmpty();
    }
}
