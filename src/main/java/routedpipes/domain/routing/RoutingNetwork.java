package routedpipes.domain.routing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

// all routers of one world with their links, route tables are worked out when asked for and kept until something changes
// big bases have thousands of routers and every one may need its own table, so inside this works on plain arrays
// routers that are connected form an island with tables of its own: a change in one base leaves the others alone,
// and a table is as big as the base it belongs to and not as the world
public final class RoutingNetwork {

    private static final int UNREACHED = Integer.MAX_VALUE;

    // routers that are connected in either direction, frozen at one moment and numbered in the order of their ids
    private static final class Island {
        private final int number;
        private final RouterId[] ids;
        private final Map<RouterId, Integer> index = new HashMap<>();
        private final int[][] targets;
        private final int[][] exits;
        private final int[][] distances;
        private final Map<RouterId, RouteTable> tables = new HashMap<>();

        private Island(int number, List<RouterId> members, Map<RouterId, List<Link>> links) {
            if (members.size() > MAX_ROUTERS) {
                throw new IllegalStateException("too many connected routers: " + members.size());
            }
            this.number = number;
            ids = members.toArray(new RouterId[0]);
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
        }

        private RouteTable routesFrom(RouterId source) {
            return tables.computeIfAbsent(source, key -> new ArrayTable(this, index.get(key)));
        }
    }

    // routes from one router as three arrays over the numbering of its island
    private static final class ArrayTable implements RouteTable {
        private final Island snapshot;
        private final int[] distance;
        private final byte[] exit;
        private final int[] nextHop;
        private List<RouterId> byDistance;

        private ArrayTable(Island snapshot, int source) {
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
    private final Map<RouterId, Island> islandOf = new HashMap<>();
    // routers that belong to no island right now: new ones, and those whose island something changed in
    private final Set<RouterId> loose = new HashSet<>();
    // who links to a router that is not there, like one in a chunk that is not loaded: when it comes back they join it
    private final Map<RouterId, Set<RouterId>> waitingFor = new HashMap<>();
    private int nextIsland;

    public void add(RouterId router) {
        if (links.putIfAbsent(router, List.of()) == null) {
            loose.add(router);
            for (RouterId other : waitingFor.getOrDefault(router, Set.of())) {
                breakUp(islandOf.get(other));
            }
            waitingFor.remove(router);
        }
    }

    public void remove(RouterId router) {
        if (links.remove(router) != null) {
            breakUp(islandOf.get(router));
            loose.remove(router);
        }
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
        breakUp(islandOf.get(router));
        for (Link link : routerLinks) {
            breakUp(islandOf.get(link.target()));
        }
        return true;
    }

    private void breakUp(Island island) {
        if (island == null) {
            return;
        }
        for (RouterId member : island.ids) {
            islandOf.remove(member);
            if (links.containsKey(member)) {
                loose.add(member);
            }
        }
    }

    // whether the router is part of something that changed and has not been put together again
    // asking for its routes, its component or its island does that
    public boolean stale(RouterId router) {
        return loose.contains(router);
    }

    // puts the loose routers into islands again
    public void settle() {
        if (loose.isEmpty()) {
            return;
        }
        Map<RouterId, List<RouterId>> neighbours = new HashMap<>();
        for (RouterId router : loose) {
            neighbours.put(router, new ArrayList<>());
        }
        for (RouterId router : loose) {
            for (Link link : links.get(router)) {
                if (loose.contains(link.target())) {
                    neighbours.get(router).add(link.target());
                    neighbours.get(link.target()).add(router);
                } else if (!links.containsKey(link.target())) {
                    waitingFor.computeIfAbsent(link.target(), key -> new HashSet<>()).add(router);
                }
            }
        }
        Set<RouterId> placed = new HashSet<>();
        Deque<RouterId> open = new ArrayDeque<>();
        for (RouterId start : loose) {
            if (!placed.add(start)) {
                continue;
            }
            List<RouterId> members = new ArrayList<>();
            open.add(start);
            while (!open.isEmpty()) {
                RouterId next = open.poll();
                members.add(next);
                for (RouterId neighbour : neighbours.get(next)) {
                    if (placed.add(neighbour)) {
                        open.add(neighbour);
                    }
                }
            }
            Island island = new Island(nextIsland++, members, links);
            members.forEach(member -> islandOf.put(member, island));
        }
        loose.clear();
    }

    // whether routesFrom would have to work the table out first
    public boolean hasRoutesFrom(RouterId source) {
        Island island = islandOf.get(source);
        return island != null && island.tables.containsKey(source);
    }

    // what changed elsewhere is left as it is, only a router that is itself part of a change makes everything settle
    private Island island(RouterId router) {
        if (loose.contains(router)) {
            settle();
        }
        return islandOf.get(router);
    }

    public RouteTable routesFrom(RouterId source) {
        Island island = island(source);
        return island == null ? RouteTable.of(Map.of()) : island.routesFrom(source);
    }

    // the routers connected to the given one in either direction, itself included, empty for an unknown router
    public List<RouterId> members(RouterId router) {
        Island island = island(router);
        return island == null ? List.of() : List.of(island.ids);
    }

    public Optional<Route> route(RouterId from, RouterId to) {
        return routesFrom(from).route(to);
    }

    // routers connected to each other, in either direction, get the same number, empty for unknown routers
    // far cheaper than a route table when all that matters is whether two routers belong together
    // the number stays the same for as long as nothing changes among them
    public OptionalInt component(RouterId router) {
        Island island = island(router);
        return island == null ? OptionalInt.empty() : OptionalInt.of(island.number);
    }
}
