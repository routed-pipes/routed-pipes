package routedpipes.domain.transport;

import java.util.Objects;
import java.util.Optional;

import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;

// a batch of items shooting through a tube, in at one end and out at the other
public final class TubeItem {

    private final long id;
    private final ItemBatch batch;
    private final RouterId destination;
    private final RouterId nextHop;
    private final TransportMode mode;
    // the end it came in at, 0 or 1
    private final int entry;
    // in steps from that end, see PipeTransport.PIPE_LENGTH
    private int progress;
    // the tick this item last moved in, so being handed on does not let it move twice in one tick
    private long movedAt = Long.MIN_VALUE;

    private TubeItem(long id, ItemBatch batch, RouterId destination, RouterId nextHop, TransportMode mode, int entry,
            int progress) {
        this.id = id;
        this.batch = Objects.requireNonNull(batch);
        this.destination = destination;
        this.nextHop = nextHop;
        this.mode = Objects.requireNonNull(mode);
        this.entry = entry;
        this.progress = progress;
    }

    public static TubeItem restore(long id, ItemBatch batch, RouterId destination, RouterId nextHop, TransportMode mode,
            int entry, int progress) {
        return new TubeItem(id, batch, destination, nextHop, mode, entry, progress);
    }

    // an item coming out of a pipe, what it overshot the end of the pipe by is not lost
    public static TubeItem entering(TravellingItem item, int entry) {
        TubeItem next = new TubeItem(item.id(), item.batch(), item.destination().orElse(null), item.nextHop().orElse(null),
                item.mode(), entry, Math.max(0, item.progress() - PipeTransport.PIPE_LENGTH));
        next.movedAt = item.movedAt();
        return next;
    }

    // the same item at the start of the next tube, after a tube of the given length
    public TubeItem enteringNext(int nextEntry, int length) {
        TubeItem next = new TubeItem(id, batch, destination, nextHop, mode, nextEntry, Math.max(0, progress - length));
        next.movedAt = movedAt;
        return next;
    }

    // the same item at the start of a pipe, back at the pace pipes move it at
    public TravellingItem leaving(Side origin) {
        return TravellingItem.fromTube(id, batch, origin, destination, nextHop, mode, movedAt);
    }

    // stays the same from tube to tube and in pipes, so a viewer can tell which item is which
    public long id() {
        return id;
    }

    public ItemBatch batch() {
        return batch;
    }

    public Optional<RouterId> destination() {
        return Optional.ofNullable(destination);
    }

    public Optional<RouterId> nextHop() {
        return Optional.ofNullable(nextHop);
    }

    public TransportMode mode() {
        return mode;
    }

    public int entry() {
        return entry;
    }

    public int progress() {
        return progress;
    }

    // back to one tick before the end, for an item that cannot leave yet
    public void holdBefore(int length) {
        progress = length - TubeTransport.SPEED;
    }

    boolean movedIn(long tick) {
        return movedAt == tick;
    }

    void advance(long tick) {
        progress += TubeTransport.SPEED;
        movedAt = tick;
    }
}
