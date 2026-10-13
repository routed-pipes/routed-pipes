package routedpipes.domain.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import routedpipes.domain.tube.TubeKind;

// inventory system connectors: routers on the same channel that each sit on an inventory are neighbours
// what one of them puts into its inventory the other takes out again, so the inventory is the pipe between them
// teleport pipes are the direct kind: they hand over through a free side with nothing in between
// the two kinds do not mix, a connector only finds the ones of its own kind on its channel
public final class ConnectorLinks {

    public enum State {
        // nobody else is on the channel
        DISCONNECTED,
        // somebody is, but this connector has no inventory or free side to reach them through
        MISMATCHED,
        CONNECTED
    }

    public static final int MAX_RESISTANCE = 9999;

    // side is where its inventory is, for the direct kind a side with nothing on it, null while it has none
    private record Connector(UUID channel, boolean direct, Side side, int resistance) {
    }

    private final Map<RouterId, Connector> connectors = new HashMap<>();
    private final Map<UUID, Set<RouterId>> channels = new HashMap<>();

    public boolean isEmpty() {
        return connectors.isEmpty();
    }

    // puts a connector on a channel or tells what changed about it, a null channel takes it off
    // returns the routers whose links may be different now
    public Set<RouterId> set(RouterId router, UUID channel, Side inventory, int resistance) {
        return set(router, channel, false, inventory, resistance);
    }

    // the same for a teleport pipe, free is a side of it that nothing is connected to
    public Set<RouterId> setDirect(RouterId router, UUID channel, Side free, int resistance) {
        return set(router, channel, true, free, resistance);
    }

    private Set<RouterId> set(RouterId router, UUID channel, boolean direct, Side side, int resistance) {
        if (channel == null) {
            return remove(router);
        }
        Connector connector = new Connector(channel, direct, side, Math.max(0, Math.min(resistance, MAX_RESISTANCE)));
        if (connector.equals(connectors.get(router))) {
            return Set.of();
        }
        Set<RouterId> touched = new HashSet<>(remove(router));
        connectors.put(router, connector);
        channels.computeIfAbsent(channel, key -> new HashSet<>()).add(router);
        touched.addAll(channels.get(channel));
        return touched;
    }

    public Set<RouterId> remove(RouterId router) {
        Connector connector = connectors.remove(router);
        if (connector == null) {
            return Set.of();
        }
        Set<RouterId> members = channels.get(connector.channel());
        Set<RouterId> touched = new HashSet<>(members);
        members.remove(router);
        if (members.isEmpty()) {
            channels.remove(connector.channel());
        }
        return touched;
    }

    // the others of its kind on the channel of this connector
    private List<RouterId> partners(RouterId router, Connector connector) {
        List<RouterId> partners = new ArrayList<>(channels.get(connector.channel()));
        partners.remove(router);
        partners.removeIf(partner -> connectors.get(partner).direct() != connector.direct());
        partners.sort(Comparator.comparing(RouterId::value));
        return partners;
    }

    // a link through its side to every other connector of its kind on the channel that has a side too
    // it counts as one pipe plus the resistance, which makes it less attractive than a way through pipes
    public List<Link> linksOf(RouterId router) {
        Connector connector = connectors.get(router);
        if (connector == null || connector.side() == null) {
            return List.of();
        }
        List<Link> links = new ArrayList<>();
        for (RouterId partner : partners(router, connector)) {
            if (connectors.get(partner).side() != null) {
                links.add(new Link(partner, connector.side(), (1 + connector.resistance()) * TubeKind.PIPE_WEIGHT));
            }
        }
        return links;
    }

    // the links of a teleport pipe, none for an inventory connector
    public List<Link> directLinksOf(RouterId router) {
        Connector connector = connectors.get(router);
        return connector != null && connector.direct() ? linksOf(router) : List.of();
    }

    // whether what leaves the first router through the given side is picked up by the second
    public boolean linked(RouterId from, Side exit, RouterId to) {
        return linksOf(from).stream().anyMatch(link -> link.target().equals(to) && link.exit() == exit);
    }

    public State state(RouterId router) {
        Connector connector = connectors.get(router);
        if (connector == null || partners(router, connector).isEmpty()) {
            return State.DISCONNECTED;
        }
        return connector.side() == null ? State.MISMATCHED : State.CONNECTED;
    }
}
