package logisticspipes.application;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.PassiveSupplier;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.Departure;
import logisticspipes.domain.transport.PipeTransport;
import logisticspipes.domain.transport.TransportMode;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.testing.FakeNode;
import logisticspipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

// a row of routed pipes that get broken and placed at random while items move through them
// every item fed in has to end up in an inventory, in a pipe or on the ground, never gone or doubled
class RandomTransportTest {

    private static final long SEED = 20261012L;
    private static final int SLOTS = 6;
    private static final int TICKS = 6000;
    private static final List<String> TYPES = List.of("coal", "stone");

    private static final class Slot {
        RouterId id;
        FakeNode node;
        PipeTransport transport;
    }

    private final Random random = new Random(SEED);
    private final LogisticsNetwork network = new LogisticsNetwork();
    private final Slot[] slots = new Slot[SLOTS];
    private int nextId = 100;
    private long fed;
    private long delivered;
    private long dropped;

    @Test
    void itemsAreNeverLostOrDoubled() {
        long now = 0;
        for (int index = 0; index < SLOTS; index++) {
            place(index, now);
        }
        for (now = 1; now <= TICKS; now++) {
            shake(now);
            feed();
            List<Integer> order = new ArrayList<>();
            for (int index = 0; index < SLOTS; index++) {
                order.add(index);
            }
            Collections.shuffle(order, random);
            for (int index : order) {
                if (slots[index] != null) {
                    tick(index, now);
                }
            }
            long travelling = travelling();
            assertThat(delivered + dropped + travelling)
                    .as("seed %d tick %d: fed %d, delivered %d, dropped %d, travelling %d", SEED, now, fed, delivered,
                            dropped, travelling)
                    .isEqualTo(fed);
        }
    }

    private void place(int index, long now) {
        Slot slot = new Slot();
        slot.id = router(nextId++);
        slot.node = new FakeNode(PipeKind.CHASSIS);
        slot.transport = new PipeTransport();
        if (random.nextInt(10) < 7) {
            slot.node.inventories.add(Side.UP);
        }
        slot.node.room = random.nextBoolean();
        slot.node.sink = switch (random.nextInt(3)) {
            case 0 -> new ItemSink(Set.of(), true);
            case 1 -> new PassiveSupplier(Map.of(new TestItem(TYPES.get(random.nextInt(TYPES.size()))), 1 + random.nextInt(20)));
            default -> null;
        };
        slots[index] = slot;
        network.addRouter(slot.id, slot.node, now);
        relink(now);
    }

    private void breakAt(int index, long now) {
        Slot slot = slots[index];
        slots[index] = null;
        network.removeRouter(slot.id, now);
        // a broken pipe drops what was inside it
        for (TravellingItem item : slot.transport.clear()) {
            dropped += item.batch().count();
        }
        relink(now);
    }

    private void relink(long now) {
        for (int index = 0; index < SLOTS; index++) {
            if (slots[index] == null) {
                continue;
            }
            List<Link> links = new ArrayList<>();
            if (index > 0 && slots[index - 1] != null) {
                links.add(new Link(slots[index - 1].id, Side.WEST, 1));
            }
            if (index < SLOTS - 1 && slots[index + 1] != null) {
                links.add(new Link(slots[index + 1].id, Side.EAST, 1));
            }
            network.updateLinks(slots[index].id, links, now);
        }
    }

    private void shake(long now) {
        int index = random.nextInt(SLOTS);
        int roll = random.nextInt(100);
        if (roll < 2) {
            if (slots[index] == null) {
                place(index, now);
            } else {
                breakAt(index, now);
            }
        } else if (roll < 6 && slots[index] != null) {
            slots[index].node.room = !slots[index].node.room;
        } else if (roll < 8 && slots[index] != null) {
            // somebody takes things out of the chest
            slots[index].node.contents.clear();
        }
    }

    private void feed() {
        if (random.nextInt(4) != 0) {
            return;
        }
        int index = random.nextInt(SLOTS);
        if (slots[index] == null) {
            return;
        }
        ItemBatch batch = batch(TYPES.get(random.nextInt(TYPES.size())), 1 + random.nextInt(64));
        fed += batch.count();
        slots[index].transport.insert(new TravellingItem(batch, Side.UP, null));
    }

    // what PipeBlockEntity.depart does for a routed pipe
    private void tick(int index, long now) {
        Slot slot = slots[index];
        for (Departure departure : slot.transport.tick(now, item -> network.chooseExit(slot.id, item, now))) {
            TravellingItem item = departure.item();
            if (departure.exit().isEmpty()) {
                dropped += item.batch().count();
                continue;
            }
            Side exit = departure.exit().get();
            int neighbour = exit == Side.EAST ? index + 1 : exit == Side.WEST ? index - 1 : -1;
            if (neighbour >= 0 && neighbour < SLOTS && slots[neighbour] != null) {
                slots[neighbour].transport.insert(item.enteringFrom(exit.opposite()));
                continue;
            }
            if (exit == Side.UP && slot.node.inventories.contains(Side.UP) && slot.node.room) {
                delivered += item.batch().count();
                slot.node.contents.merge(item.batch().type(), item.batch().count(), Integer::sum);
                continue;
            }
            if (item.mode() == TransportMode.REQUESTED && item.destination().filter(slot.id::equals).isPresent()) {
                slot.transport.insert(item.remainder(item.batch()));
            } else {
                slot.transport.insert(new TravellingItem(item.batch(), exit, null));
            }
        }
    }

    private long travelling() {
        long count = 0;
        for (Slot slot : slots) {
            if (slot != null) {
                for (TravellingItem item : slot.transport.items()) {
                    count += item.batch().count();
                }
            }
        }
        return count;
    }
}
