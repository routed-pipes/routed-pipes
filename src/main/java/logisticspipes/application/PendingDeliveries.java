package logisticspipes.application;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.routing.RouterId;

// items that were sent to a router and have not arrived yet, lost ones are forgotten after their deadline
final class PendingDeliveries {

    private static final class Expected {
        private int count;
        private final long deadline;

        private Expected(int count, long deadline) {
            this.count = count;
            this.deadline = deadline;
        }
    }

    private record Key(RouterId destination, ItemType type) {
    }

    // in the order they were first expected
    private final Map<Key, Deque<Expected>> expected = new LinkedHashMap<>();

    void expect(RouterId destination, ItemType type, int count, long deadline) {
        if (count > 0) {
            expected.computeIfAbsent(new Key(destination, type), key -> new ArrayDeque<>()).add(new Expected(count, deadline));
        }
    }

    void arrived(RouterId destination, ItemType type, int count) {
        Key key = new Key(destination, type);
        Deque<Expected> queue = expected.get(key);
        int left = count;
        while (queue != null && left > 0 && !queue.isEmpty()) {
            Expected oldest = queue.peek();
            int matched = Math.min(left, oldest.count);
            oldest.count -= matched;
            left -= matched;
            if (oldest.count == 0) {
                queue.poll();
            }
        }
        if (queue != null && queue.isEmpty()) {
            expected.remove(key);
        }
    }

    int expected(RouterId destination, ItemType type, long now) {
        Key key = new Key(destination, type);
        Deque<Expected> queue = expected.get(key);
        if (queue == null) {
            return 0;
        }
        queue.removeIf(entry -> entry.deadline <= now);
        if (queue.isEmpty()) {
            expected.remove(key);
            return 0;
        }
        return queue.stream().mapToInt(entry -> entry.count).sum();
    }

    // everything that is still on its way to a router
    Map<ItemType, Integer> expectedAt(RouterId destination, long now) {
        Map<ItemType, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<Key, Deque<Expected>> entry : expected.entrySet()) {
            if (entry.getKey().destination().equals(destination)) {
                int count = entry.getValue().stream().filter(one -> one.deadline > now).mapToInt(one -> one.count).sum();
                if (count > 0) {
                    counts.put(entry.getKey().type(), count);
                }
            }
        }
        return counts;
    }

    void forget(RouterId destination) {
        expected.keySet().removeIf(key -> key.destination().equals(destination));
    }
}
