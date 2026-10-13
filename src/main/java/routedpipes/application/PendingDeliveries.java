package routedpipes.application;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import routedpipes.domain.item.ItemType;
import routedpipes.domain.routing.RouterId;

// items that were sent to a router and have not arrived yet, lost ones are forgotten after their deadline
final class PendingDeliveries {

    private static final class Expected {
        private int count;
        private long deadline;

        private Expected(int count, long deadline) {
            this.count = count;
            this.deadline = deadline;
        }
    }

    // per router and in the order they were first expected
    // a crafter with a fuzzy result is expected under its fuzzy type, whatever it makes counts for that
    private final Map<RouterId, Map<ItemType, Deque<Expected>>> expected = new HashMap<>();

    void expect(RouterId destination, ItemType type, int count, long deadline) {
        if (count > 0) {
            expected.computeIfAbsent(destination, key -> new LinkedHashMap<>()).computeIfAbsent(type, key -> new ArrayDeque<>())
                    .add(new Expected(count, deadline));
        }
    }

    void arrived(RouterId destination, ItemType type, int count) {
        Map<ItemType, Deque<Expected>> here = expected.get(destination);
        if (here == null) {
            return;
        }
        int left = arrived(here.get(type), count);
        for (Map.Entry<ItemType, Deque<Expected>> entry : here.entrySet()) {
            if (left > 0 && !entry.getKey().equals(type) && entry.getKey().accepts(type)) {
                left = arrived(entry.getValue(), left);
            }
        }
        here.values().removeIf(Deque::isEmpty);
        if (here.isEmpty()) {
            expected.remove(destination);
        }
    }

    // what is left of the count after the oldest expectations took theirs
    private static int arrived(Deque<Expected> queue, int count) {
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
        return left;
    }

    int expected(RouterId destination, ItemType type, long now) {
        Map<ItemType, Deque<Expected>> here = expected.get(destination);
        if (here == null) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<ItemType, Deque<Expected>> entry : here.entrySet()) {
            if (entry.getKey().accepts(type)) {
                entry.getValue().removeIf(one -> one.deadline <= now);
                count += entry.getValue().stream().mapToInt(one -> one.count).sum();
            }
        }
        here.values().removeIf(Deque::isEmpty);
        if (here.isEmpty()) {
            expected.remove(destination);
        }
        return count;
    }

    // everything that is still on its way to a router
    Map<ItemType, Integer> expectedAt(RouterId destination, long now) {
        Map<ItemType, Integer> counts = new LinkedHashMap<>();
        expected.getOrDefault(destination, Map.of()).forEach((type, queue) -> {
            int count = queue.stream().filter(one -> one.deadline > now).mapToInt(one -> one.count).sum();
            if (count > 0) {
                counts.merge(type.exact(), count, Integer::sum);
            }
        });
        return counts;
    }

    // what is still expected keeps being expected until at least the deadline
    void extend(RouterId destination, ItemType type, long deadline) {
        Deque<Expected> queue = expected.getOrDefault(destination, Map.of()).get(type);
        if (queue != null) {
            queue.forEach(entry -> entry.deadline = Math.max(entry.deadline, deadline));
        }
    }

    void forget(RouterId destination) {
        expected.remove(destination);
    }
}
