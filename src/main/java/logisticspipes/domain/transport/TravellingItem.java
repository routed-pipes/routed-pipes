package logisticspipes.domain.transport;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.routing.RouterId;

// a batch of items moving through one pipe, from the origin side to the centre and out through the exit
// the exit is decided on entry, an item that finds none waits a moment and then stops at the centre
public final class TravellingItem {

    private final long id;
    private final ItemBatch batch;
    private final Side origin;
    private RouterId destination;
    // the router this item is passed to next, plain pipes need it at forks
    private RouterId nextHop;
    private Side exit;
    private int progress;
    private boolean decided;
    private boolean arrived;
    private int waited;

    public TravellingItem(ItemBatch batch, Side origin, RouterId destination) {
        this(ThreadLocalRandom.current().nextLong(), batch, origin, destination);
    }

    private TravellingItem(long id, ItemBatch batch, Side origin, RouterId destination) {
        this.id = id;
        this.batch = Objects.requireNonNull(batch);
        this.origin = Objects.requireNonNull(origin);
        this.destination = destination;
    }

    public static TravellingItem restore(long id, ItemBatch batch, Side origin, RouterId destination, RouterId nextHop,
            Side exit, int progress) {
        TravellingItem item = new TravellingItem(id, batch, origin, destination);
        item.nextHop = nextHop;
        item.exit = exit;
        item.decided = exit != null;
        item.progress = progress;
        return item;
    }

    // the same item at the start of the next pipe
    public TravellingItem enteringFrom(Side side) {
        TravellingItem next = new TravellingItem(id, batch, side, destination);
        next.nextHop = nextHop;
        return next;
    }

    // stays the same from pipe to pipe, so a viewer can tell which item is which
    public long id() {
        return id;
    }

    public ItemBatch batch() {
        return batch;
    }

    public Side origin() {
        return origin;
    }

    public Optional<RouterId> destination() {
        return Optional.ofNullable(destination);
    }

    public void routeTo(RouterId router) {
        destination = Objects.requireNonNull(router);
    }

    public void clearDestination() {
        destination = null;
    }

    public Optional<RouterId> nextHop() {
        return Optional.ofNullable(nextHop);
    }

    public void passTo(RouterId router) {
        nextHop = router;
    }

    public Optional<Side> exit() {
        return Optional.ofNullable(exit);
    }

    public int progress() {
        return progress;
    }

    public boolean decided() {
        return decided;
    }

    // true the first time only, so an arrival is never counted twice
    public boolean markArrived() {
        boolean first = !arrived;
        arrived = true;
        return first;
    }

    int waited() {
        return waited;
    }

    void waitForExit() {
        waited++;
    }

    void advance() {
        progress++;
    }

    void decide(Optional<Side> side) {
        exit = side.orElse(null);
        decided = true;
    }
}
