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
    // how long this item takes to cross one pipe
    private int ticksPerPipe = PipeTransport.TICKS_PER_PIPE;
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
        next.ticksPerPipe = ticksPerPipe;
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

    public int ticksPerPipe() {
        return ticksPerPipe;
    }

    public void setTicksPerPipe(int ticks) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("ticks per pipe must be positive: " + ticks);
        }
        ticksPerPipe = ticks;
    }

    // true once the item has left the origin half of the pipe
    public boolean pastCentre() {
        return progress * 2 >= ticksPerPipe;
    }

    public boolean atExit() {
        return progress >= ticksPerPipe;
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

    // ticks this item has been waiting in the pipe for an exit
    public int waited() {
        return waited;
    }

    public void waitForExit() {
        waited++;
    }

    // for waits that are nobody's fault, like a network that is still loading: the item will not be given up on
    public void stayPatient() {
        waited = 0;
    }

    // what did not fit into the inventory this item was ordered for, to be tried again from the same pipe
    public TravellingItem remainder(ItemBatch rest) {
        TravellingItem again = new TravellingItem(batch.equals(rest) ? id : ThreadLocalRandom.current().nextLong(), rest, origin, destination);
        again.arrived = arrived;
        again.ticksPerPipe = ticksPerPipe;
        return again;
    }

    void advance() {
        progress++;
    }

    void decide(Optional<Side> side) {
        exit = side.orElse(null);
        decided = true;
    }
}
