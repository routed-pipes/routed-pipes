package routedpipes.domain.channel;

import java.util.UUID;

// what the security stations say about a player using the channels they are responsible for
@FunctionalInterface
public interface StationAccess {

    // false too when the station is gone
    boolean grants(UUID station, UUID player);
}
