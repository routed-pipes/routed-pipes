package routedpipes.domain.transport;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;

public final class UnroutedPipe {

    private UnroutedPipe() {
    }

    // plain pipes just pass items on, never back where they came from
    // at a fork the item takes the branch that leads to the router it is headed for
    public static Optional<Side> chooseExit(Set<Side> connections, TravellingItem item,
            Function<RouterId, Optional<Side>> branchTowards) {
        List<Side> onward = connections.stream().filter(side -> side != item.origin()).sorted().toList();
        if (onward.size() > 1) {
            Optional<Side> branch = item.nextHop().flatMap(branchTowards).filter(onward::contains);
            if (branch.isPresent()) {
                return branch;
            }
        }
        return onward.stream().findFirst();
    }
}
