package routedpipes.domain.macro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.item.ItemType;

// a named list of items with amounts that is ordered in one go
public record Macro(String name, List<ItemBatch> entries) {

    // why a macro cannot be saved
    public enum Problem {
        NO_NAME,
        NO_ITEMS
    }

    public static final Macro EMPTY = new Macro("", List.of());
    // the list is sent by clients, so it is kept to a size a disk can carry
    public static final int MAX_ENTRIES = 64;

    public Macro {
        name = name.strip();
        // the same item twice is one entry with both amounts
        Map<ItemType, Integer> merged = new LinkedHashMap<>();
        entries.forEach(entry -> merged.merge(entry.type(), entry.count(),
                (had, more) -> (int) Math.min(Integer.MAX_VALUE, (long) had + more)));
        List<ItemBatch> list = new ArrayList<>();
        merged.forEach((type, count) -> list.add(new ItemBatch(type, count)));
        entries = List.copyOf(list);
    }

    public Macro named(String newName) {
        return new Macro(newName, entries);
    }

    // a new kind of item is left out once the list is full, more of one already listed is always added
    public Macro with(ItemBatch added) {
        boolean listed = entries.stream().anyMatch(entry -> entry.type().equals(added.type()));
        if (!listed && entries.size() >= MAX_ENTRIES) {
            return this;
        }
        List<ItemBatch> list = new ArrayList<>(entries);
        list.add(added);
        return new Macro(name, list);
    }

    public Macro without(ItemType type) {
        return new Macro(name, entries.stream().filter(entry -> !entry.type().equals(type)).toList());
    }

    // empty if the macro can be saved
    public Optional<Problem> problem() {
        if (name.isEmpty()) {
            return Optional.of(Problem.NO_NAME);
        }
        return entries.isEmpty() ? Optional.of(Problem.NO_ITEMS) : Optional.empty();
    }
}
