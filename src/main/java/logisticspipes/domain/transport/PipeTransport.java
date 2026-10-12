package logisticspipes.domain.transport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import logisticspipes.domain.routing.Side;

// the items currently inside one pipe
public final class PipeTransport {

    public static final int TICKS_PER_PIPE = 10;
    public static final int CENTRE = TICKS_PER_PIPE / 2;
    // how long an item waits for an exit, covers networks that are still loading
    public static final int MAX_WAIT = 40;

    private final List<TravellingItem> items = new ArrayList<>();
    // ids a viewer guessed and the real pipe has not confirmed yet
    private final Set<Long> unconfirmed = new HashSet<>();
    private boolean changed;

    public void insert(TravellingItem item) {
        items.add(item);
        changed = true;
    }

    public List<TravellingItem> items() {
        return List.copyOf(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public List<TravellingItem> clear() {
        List<TravellingItem> removed = List.copyOf(items);
        items.clear();
        unconfirmed.clear();
        changed = true;
        return removed;
    }

    // true once after something happened that a viewer could not have predicted
    public boolean pollChanged() {
        boolean result = changed;
        changed = false;
        return result;
    }

    // moves everything one step, the exit is picked when an item is first seen
    public List<Departure> tick(ExitChooser chooser) {
        List<Departure> departures = new ArrayList<>();
        for (Iterator<TravellingItem> iterator = items.iterator(); iterator.hasNext();) {
            TravellingItem item = iterator.next();
            if (!item.decided()) {
                Optional<Side> exit = chooser.chooseExit(item);
                if (exit.isEmpty() && item.waited() < MAX_WAIT) {
                    item.waitForExit();
                    continue;
                }
                item.decide(exit);
                changed = true;
            }
            item.advance();
            if (item.exit().isEmpty() && item.progress() >= CENTRE) {
                iterator.remove();
                changed = true;
                departures.add(new Departure(item, Optional.empty()));
            } else if (item.progress() >= TICKS_PER_PIPE) {
                iterator.remove();
                departures.add(new Departure(item, item.exit()));
            }
        }
        return departures;
    }

    // what a viewer does between updates: move along and hand back the items that reached their exit
    public List<Departure> advance() {
        List<Departure> departures = new ArrayList<>();
        for (Iterator<TravellingItem> iterator = items.iterator(); iterator.hasNext();) {
            TravellingItem item = iterator.next();
            if (item.exit().isEmpty()) {
                // without an exit a real item waits where it is, a guessed one fades out at the centre
                if (unconfirmed.contains(item.id())) {
                    item.advance();
                    if (item.progress() >= CENTRE) {
                        iterator.remove();
                        unconfirmed.remove(item.id());
                    }
                }
                continue;
            }
            item.advance();
            if (item.progress() >= TICKS_PER_PIPE) {
                iterator.remove();
                departures.add(new Departure(item, item.exit()));
            }
        }
        return departures;
    }

    // a viewer guessing that an item has moved in here, the real pipe confirms or corrects it later
    public void expect(TravellingItem item) {
        if (items.stream().noneMatch(known -> known.id() == item.id())) {
            items.add(item);
            unconfirmed.add(item.id());
        }
    }

    public OptionalInt progressOf(long id) {
        return items.stream().filter(item -> item.id() == id).mapToInt(TravellingItem::progress).findFirst();
    }
}
