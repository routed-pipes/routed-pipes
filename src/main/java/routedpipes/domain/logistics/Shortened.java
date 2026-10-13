package routedpipes.domain.logistics;

import java.util.Collection;
import java.util.List;

// the start of a long list and how many entries were left out
public record Shortened<T>(List<T> shown, int more) {

    public Shortened {
        shown = List.copyOf(shown);
    }

    public static <T> Shortened<T> of(Collection<T> all, int max) {
        return new Shortened<>(all.stream().limit(max).toList(), Math.max(0, all.size() - max));
    }

    public boolean isEmpty() {
        return shown.isEmpty();
    }
}
