package logisticspipes.domain.transport;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;

// the items currently inside one tube
public final class TubeTransport {

    // steps per tick, 0.8 blocks. Items are that fast from the moment they enter, whatever they did before
    public static final int SPEED = 48;

    private final List<TubeItem> items = new ArrayList<>();
    private boolean changed;

    public void insert(TubeItem item) {
        items.add(item);
        changed = true;
    }

    // a viewer guessing that an item has moved in here, the real tube confirms or corrects it later
    public void expect(TubeItem item) {
        if (items.stream().noneMatch(known -> known.id() == item.id())) {
            items.add(item);
        }
    }

    public List<TubeItem> items() {
        return List.copyOf(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public List<TubeItem> clear() {
        List<TubeItem> removed = List.copyOf(items);
        items.clear();
        changed = true;
        return removed;
    }

    // true once after something happened that a viewer could not have predicted
    public boolean pollChanged() {
        boolean result = changed;
        changed = false;
        return result;
    }

    // moves everything one step and hands back the items that reached the far end of a tube of the given length
    public List<TubeItem> tick(long now, int length) {
        List<TubeItem> through = new ArrayList<>();
        for (Iterator<TubeItem> iterator = items.iterator(); iterator.hasNext();) {
            TubeItem item = iterator.next();
            if (item.movedIn(now)) {
                // came in from a pipe or tube that ticked earlier in this same tick
                continue;
            }
            item.advance(now);
            if (item.progress() >= length) {
                iterator.remove();
                through.add(item);
            }
        }
        return through;
    }

    public OptionalInt progressOf(long id) {
        return items.stream().filter(item -> item.id() == id).mapToInt(TubeItem::progress).findFirst();
    }
}
