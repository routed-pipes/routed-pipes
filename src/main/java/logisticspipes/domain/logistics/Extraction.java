package logisticspipes.domain.logistics;

import java.util.Set;

import logisticspipes.domain.item.ItemType;

// one extractor of a pipe: how it works and which items it touches
public record Extraction(ExtractionMode mode, Set<ItemType> listed, boolean exclude) {

    public Extraction {
        listed = Set.copyOf(listed);
    }

    // takes everything
    public static Extraction of(ExtractionMode mode) {
        return new Extraction(mode, Set.of(), true);
    }

    // the list is about items whatever their damage or data, an empty list of items to take means nothing is taken
    public boolean takes(ItemType type) {
        return exclude != listed.contains(type.plain());
    }
}
