package routedpipes.domain.logistics;

import java.util.Set;

import routedpipes.domain.item.ItemType;

// one extractor of a pipe: how it works, which items it touches and the upgrades it has
public record Extraction(ExtractionMode mode, Set<ItemType> listed, boolean exclude, int actionSpeed, int itemExtraction,
        int stackExtraction) {

    public Extraction {
        listed = Set.copyOf(listed);
    }

    // takes everything, no upgrades
    public static Extraction of(ExtractionMode mode) {
        return new Extraction(mode, Set.of(), true, 0, 0, 0);
    }

    public Extraction(ExtractionMode mode, Set<ItemType> listed, boolean exclude) {
        this(mode, listed, exclude, 0, 0, 0);
    }

    public Extraction with(int actionSpeed, int itemExtraction, int stackExtraction) {
        return new Extraction(mode, listed, exclude, actionSpeed, itemExtraction, stackExtraction);
    }

    // the list is about items whatever their damage or data, an empty list of items to take means nothing is taken
    public boolean takes(ItemType type) {
        return exclude != listed.contains(type.plain());
    }

    // ticks between rounds, halved by every action speed upgrade
    public int interval() {
        return Math.max(1, mode.interval() >> actionSpeed);
    }

    // items per round
    public int amount() {
        return mode.amount() * (1 + itemExtraction) + 64 * stackExtraction;
    }

    // how many kinds of items go out per round
    public int stacks() {
        return 1 + stackExtraction;
    }

    // faster extractors pay more per item
    public int energyFor(int items) {
        double factor = Math.pow(1.1, itemExtraction) * Math.pow(1.2, stackExtraction);
        return (int) (mode.energyFor(items) * factor);
    }
}
