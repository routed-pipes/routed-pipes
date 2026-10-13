package routedpipes.domain.transport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import routedpipes.domain.routing.Side;

// the items currently inside one pipe
public final class PipeTransport {

    // a pipe is this many steps long, fine enough for every speed to be a whole number of steps per tick
    public static final int PIPE_LENGTH = 60;
    // ticks an item waits for an exit before it is given up on
    public static final int MAX_WAIT = 200;

    private final List<TravellingItem> items = new ArrayList<>();
    // ids a viewer guessed and the real pipe has not confirmed yet
    private final Set<Long> unconfirmed = new HashSet<>();
    private boolean changed;
    // routed pipes set the pace of every item that passes, in percent of the speed its mode has
    // plain pipes have none and leave items as fast as they came
    private Integer boost;

    public void setBoost(Integer percent) {
        boost = percent;
    }

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
    public List<Departure> tick(long now, ExitChooser chooser) {
        List<Departure> departures = new ArrayList<>();
        List<TravellingItem> cutOff = new ArrayList<>();
        for (Iterator<TravellingItem> iterator = items.iterator(); iterator.hasNext();) {
            TravellingItem item = iterator.next();
            if (item.movedIn(now)) {
                // came in from a pipe that ticked earlier in this same tick
                continue;
            }
            if (!item.decided()) {
                Optional<Side> exit = chooser.chooseExit(item);
                // a destination that wants only part of the item leaves the rest here to find another one
                item.takeCutOff().ifPresent(rest -> cutOff.add(new TravellingItem(rest, item.origin(), null)));
                if (exit.isEmpty() && !item.abandoned() && item.waited() < MAX_WAIT) {
                    item.waitForExit();
                    continue;
                }
                item.decide(exit);
                if (boost != null) {
                    item.setSpeed(item.mode().speed() * boost / 100);
                }
                changed = true;
            }
            item.advance(now);
            if (item.exit().isEmpty() && item.pastCentre()) {
                iterator.remove();
                changed = true;
                departures.add(new Departure(item, Optional.empty()));
            } else if (item.atExit()) {
                iterator.remove();
                departures.add(new Departure(item, item.exit()));
            }
        }
        if (!cutOff.isEmpty()) {
            items.addAll(cutOff);
            changed = true;
        }
        return departures;
    }

    // what a viewer does between updates: move along and hand back the items that reached their exit
    public List<Departure> advance(long now) {
        List<Departure> departures = new ArrayList<>();
        for (Iterator<TravellingItem> iterator = items.iterator(); iterator.hasNext();) {
            TravellingItem item = iterator.next();
            if (item.movedIn(now)) {
                continue;
            }
            if (item.exit().isEmpty()) {
                // without an exit a real item waits where it is, a guessed one fades out at the centre
                if (unconfirmed.contains(item.id())) {
                    item.advance(now);
                    if (item.pastCentre()) {
                        iterator.remove();
                        unconfirmed.remove(item.id());
                    }
                }
                continue;
            }
            item.advance(now);
            if (item.atExit()) {
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
