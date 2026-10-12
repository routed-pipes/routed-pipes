package logisticspipes.domain.listing;

import java.util.List;

// one page of a longer list, a page number outside the list ends up on the nearest page there is
public record Page<T>(int number, int pages, List<T> entries) {

    public static <T> Page<T> of(List<T> all, int number, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("page size must be positive: " + size);
        }
        int pages = Math.max(1, (all.size() + size - 1) / size);
        int page = Math.max(0, Math.min(number, pages - 1));
        int from = Math.min(page * size, all.size());
        return new Page<>(page, pages, List.copyOf(all.subList(from, Math.min(from + size, all.size()))));
    }
}
