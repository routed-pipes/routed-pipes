package logisticspipes.domain.transport;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.routing.RouterId;

// a batch of items moving through one pipe, from the origin side to the centre and out through the exit
// the exit is decided on entry, an item that finds none waits a while and then stops at the centre
public final class TravellingItem {

    private final long id;
    private final ItemBatch batch;
    private final Side origin;
    private RouterId destination;
    // the router this item is passed to next, plain pipes need it at forks
    private RouterId nextHop;
    private TransportMode mode;
    private Side exit;
    // in steps, see PipeTransport.PIPE_LENGTH
    private int progress;
    // steps per tick
    private int speed;
    private boolean decided;
    private boolean arrived;
    private boolean abandoned;
    private int waited;
    // the tick this item last moved in, so being handed to the next pipe does not let it move twice in one tick
    private long movedAt = Long.MIN_VALUE;

    // an item with a destination from the start was ordered, one without is waiting to be claimed
    public TravellingItem(ItemBatch batch, Side origin, RouterId destination) {
        this(ThreadLocalRandom.current().nextLong(), batch, origin, destination,
                destination == null ? TransportMode.UNCLAIMED : TransportMode.REQUESTED);
    }

    private TravellingItem(long id, ItemBatch batch, Side origin, RouterId destination, TransportMode mode) {
        this.id = id;
        this.batch = Objects.requireNonNull(batch);
        this.origin = Objects.requireNonNull(origin);
        this.destination = destination;
        this.mode = mode;
        this.speed = mode.speed();
    }

    public static TravellingItem restore(long id, ItemBatch batch, Side origin, RouterId destination, RouterId nextHop,
            TransportMode mode, Side exit, int progress, int speed) {
        TravellingItem item = new TravellingItem(id, batch, origin, destination, mode);
        item.nextHop = nextHop;
        item.exit = exit;
        item.decided = exit != null;
        item.progress = progress;
        item.setSpeed(speed);
        return item;
    }

    // the same item at the start of the next pipe, what it overshot the end of this one by is not lost
    public TravellingItem enteringFrom(Side side) {
        TravellingItem next = new TravellingItem(id, batch, side, destination, mode);
        next.nextHop = nextHop;
        next.speed = speed;
        next.progress = Math.max(0, progress - PipeTransport.PIPE_LENGTH);
        next.movedAt = movedAt;
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

    public TransportMode mode() {
        return mode;
    }

    // a sink has claimed the item
    public void routeTo(RouterId router) {
        destination = Objects.requireNonNull(router);
        mode = TransportMode.SORTED;
    }

    public void clearDestination() {
        destination = null;
        mode = TransportMode.UNCLAIMED;
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

    public int speed() {
        return speed;
    }

    // never more than one whole pipe per tick
    public void setSpeed(int stepsPerTick) {
        if (stepsPerTick <= 0) {
            throw new IllegalArgumentException("speed must be positive: " + stepsPerTick);
        }
        speed = Math.min(stepsPerTick, PipeTransport.PIPE_LENGTH);
    }

    // true once the item has left the origin half of the pipe
    public boolean pastCentre() {
        return progress * 2 >= PipeTransport.PIPE_LENGTH;
    }

    public boolean atExit() {
        return progress >= PipeTransport.PIPE_LENGTH;
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

    // there will never be an exit here, like an order arriving at a pipe without an inventory: no point in waiting
    public void abandon() {
        abandoned = true;
    }

    public boolean abandoned() {
        return abandoned;
    }

    // what did not fit into the inventory this item was ordered for, to be tried again from the same pipe
    public TravellingItem remainder(ItemBatch rest) {
        long restId = batch.equals(rest) ? id : ThreadLocalRandom.current().nextLong();
        TravellingItem again = new TravellingItem(restId, rest, origin, destination, mode);
        again.arrived = arrived;
        again.speed = speed;
        return again;
    }

    boolean movedIn(long tick) {
        return movedAt == tick;
    }

    void advance(long tick) {
        progress += speed;
        movedAt = tick;
    }

    void decide(Optional<Side> side) {
        exit = side.orElse(null);
        decided = true;
    }
}
