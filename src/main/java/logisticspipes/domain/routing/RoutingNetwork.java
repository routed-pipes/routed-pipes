package logisticspipes.domain.routing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

// all routers of one world with their links, route tables are worked out when asked for and kept until something changes
// big bases have thousands of routers and every one may need its own table, so inside this works on plain arrays
public final class RoutingNetwork {

    private static final int UNREACHED = Integer.MAX_VALUE;

    // the network frozen at one moment, with routers numbered in the order of their ids
    private static final class Snapshot {
        private final RouterId[] ids;
        private final Map<RouterId, Integer> index = new HashMap<>();
        private final int[][] targets;
        private final int[][] exits;
        private final int[][] distances;
        private final int[] component;

        private Snapshot(Map<RouterId, List<Link>> links) {
            ids = links.keySet().toArray(new RouterId[0]);
            Arrays.sort(ids, Comparator.comparing(RouterId::value));
            for (int i = 0; i < ids.length; i++) {
                index.put(ids[i], i);
            }
            targets = new int[ids.length][];
            exits = new int[ids.length][];
            distances = new int[ids.length][];
            for (int i = 0; i < ids.length; i++) {
                // links can still point at routers that are gone, for example in unloaded chunks
                List<Link> known = links.get(ids[i]).stream().filter(link -> index.containsKey(link.target())).toList();
                targets[i] = new int[known.size()];
                exits[i] = new int[known.size()];
                distances[i] = new int[known.size()];
                for (int link = 0; link < known.size(); link++) {
                    targets[i][link] = index.get(known.get(link).target());
                    exits[i][link] = known.get(link).exit().ordinal();
                    distances[i][link] = known.get(link).distance();
                }
            }
            component = components();
        }

        // routers that are connected in either direction share a number
        private int[] components() {
            List<List<Integer>> neighbours = new ArrayList<>();
            for (int i = 0; i < ids.length; i++) {
                neighbours.add(new ArrayList<>());
            }
            for (int i = 0; i < ids.length; i++) {
                for (int target : targets[i]) {
                    neighbours.get(i).add(target);
                    neighbours.get(target).add(i);
                }
            }
            int[] result = new int[ids.length];
            Arrays.fill(result, -1);
            int next = 0;
            Deque<Integer> open = new ArrayDeque<>();
            for (int start = 0; start < ids.length; start++) {
                if (result[start] >= 0) {
                    continue;
                }
                result[start] = next;
                open.add(start);
                while (!open.isEmpty()) {
                    for (int neighbour : neighbours.get(open.poll())) {
                        if (result[neighbour] < 0) {
                            result[neighbour] = next;
                            open.add(neighbour);
                        }
                    }
                }
                next++;
            }
            return result;
        }
    }

    // routes from one router as three arrays over the numbering of the snapshot
    private static final class ArrayTable implements RouteTable {
        private final Snapshot snapshot;
        private final int[] distance;
        private final byte[] exit;
        private final int[] nextHop;
        private List<RouterId> byDistance;

        private ArrayTable(Snapshot snapshot, int source) {
            this.snapshot = snapshot;
            int count = snapshot.ids.length;
            distance = new int[count];
            exit = new byte[count];
            nextHop = new int[count];
            Arrays.fill(distance, UNREACHED);
            calculate(source);
        }

        // the same search as RouteCalculator, with the same order among equally long routes:
        // by distance, then the id of the router reached, then the exit, then the id of the first router on the way
        // one entry of the queue packs these four into a long so that comparing longs compares them in that order
        private void calculate(int source) {
            LongHeap open = new LongHeap();
            for (int link = 0; link < snapshot.targets[source].length; link++) {
                int target = snapshot.targets[source][link];
                open.add(pack(snapshot.distances[source][link], target, snapshot.exits[source][link], target));
            }
            while (!open.isEmpty()) {
                long entry = open.poll();
                int reached = (int) (entry >>> 19) & 0xFFFF;
                if (reached == source || distance[reached] != UNREACHED) {
                    continue;
                }
                int total = (int) (entry >>> 35);
                int through = (int) (entry >>> 16) & 0x7;
                int first = (int) entry & 0xFFFF;
                distance[reached] = total;
                exit[reached] = (byte) through;
                nextHop[reached] = first;
                for (int link = 0; link < snapshot.targets[reached].length; link++) {
                    int target = snapshot.targets[reached][link];
                    if (distance[target] == UNREACHED) {
                        open.add(pack(total + snapshot.distances[reached][link], target, through, first));
                    }
                }
            }
        }

        private static long pack(int distance, int reached, int exit, int first) {
            return (long) distance << 35 | (long) reached << 19 | (long) exit << 16 | first;
        }

        @Override
        public Optional<Route> route(RouterId destination) {
            Integer index = snapshot.index.get(destination);
            if (index == null || distance[index] == UNREACHED) {
                return Optional.empty();
            }
            return Optional.of(new Route(destination, Side.values()[exit[index]], snapshot.ids[nextHop[index]], distance[index]));
        }

        @Override
        public OptionalInt distance(RouterId destination) {
            Integer index = snapshot.index.get(destination);
            return index == null || distance[index] == UNREACHED ? OptionalInt.empty() : OptionalInt.of(distance[index]);
        }

        @Override
        public List<RouterId> byDistance() {
            if (byDistance == null) {
                List<Integer> reached = new ArrayList<>();
                for (int i = 0; i < distance.length; i++) {
                    if (distance[i] != UNREACHED) {
                        reached.add(i);
                    }
                }
                // the numbering already follows the ids, so a stable sort by distance breaks ties by id
                reached.sort(Comparator.comparingInt(i -> distance[i]));
                byDistance = reached.stream().map(i -> snapshot.ids[i]).toList();
            }
            return byDistance;
        }
    }

    // a small binary heap of longs, smallest first
    private static final class LongHeap {
        private long[] values = new long[64];
        private int size;

        private boolean isEmpty() {
            return size == 0;
        }

        private void add(long value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            int at = size++;
            while (at > 0 && values[(at - 1) / 2] > value) {
                values[at] = values[(at - 1) / 2];
                at = (at - 1) / 2;
            }
            values[at] = value;
        }

        private long poll() {
            long smallest = values[0];
            long last = values[--size];
            int at = 0;
            while (true) {
                int child = 2 * at + 1;
                if (child >= size) {
                    break;
                }
                if (child + 1 < size && values[child + 1] < values[child]) {
                    child++;
                }
                if (values[child] >= last) {
                    break;
                }
                values[at] = values[child];
                at = child;
            }
            values[at] = last;
            return smallest;
        }
    }

    // the packed queue entries have room for this many routers
    static final int MAX_ROUTERS = 1 << 16;

    private final Map<RouterId, List<Link>> links = new HashMap<>();
    private final Map<RouterId, RouteTable> tables = new HashMap<>();
    private Snapshot snapshot;

    public void add(RouterId router) {
        if (links.size() >= MAX_ROUTERS && !links.containsKey(router)) {
            throw new IllegalStateException("too many routers in one world: " + links.size());
        }
        links.putIfAbsent(router, List.of());
        changed();
    }

    public void remove(RouterId router) {
        links.remove(router);
        changed();
    }

    public boolean contains(RouterId router) {
        return links.containsKey(router);
    }

    // returns whether anything changed
    public boolean setLinks(RouterId router, List<Link> routerLinks) {
        if (!links.containsKey(router)) {
            throw new IllegalArgumentException("unknown router: " + router);
        }
        if (links.get(router).equals(routerLinks)) {
            return false;
        }
        links.put(router, List.copyOf(routerLinks));
        changed();
        return true;
    }

    private void changed() {
        tables.clear();
        snapshot = null;
    }

    private Snapshot snapshot() {
        if (snapshot == null) {
            snapshot = new Snapshot(links);
        }
        return snapshot;
    }

    public RouteTable routesFrom(RouterId source) {
        RouteTable table = tables.get(source);
        if (table == null) {
            Integer index = snapshot().index.get(source);
            table = index == null ? RouteTable.of(Map.of()) : new ArrayTable(snapshot(), index);
            tables.put(source, table);
        }
        return table;
    }

    public Optional<Route> route(RouterId from, RouterId to) {
        return routesFrom(from).route(to);
    }

    // routers connected to each other, in either direction, get the same number, empty for unknown routers
    // far cheaper than a route table when all that matters is whether two routers belong together
    public OptionalInt component(RouterId router) {
        Integer index = snapshot().index.get(router);
        return index == null ? OptionalInt.empty() : OptionalInt.of(snapshot().component[index]);
    }
}
