package routedpipes.domain.item;

// what a marker slot with fuzzy flags asks for: the item in it or anything else the flags let pass
// it never names a real stack, exact gives the item of the slot for showing it to players
public record FuzzyType(ItemType item, FuzzyFlags flags) implements ItemType {

    @Override
    public boolean accepts(ItemType other) {
        return flags.matches(item, other.exact());
    }

    @Override
    public ItemType exact() {
        return item;
    }
}
