package routedpipes.domain.channel;

import java.util.UUID;

// a named link that inventory system connectors are put on, station is only set for a secured one
public record Channel(UUID id, String name, UUID owner, String ownerName, ChannelAccess access, UUID station) {

    public boolean ownedBy(UUID player) {
        return owner.equals(player);
    }

    // a secured channel whose station is gone or does not list the player is as good as private
    public boolean allows(UUID player, StationAccess stations) {
        return access == ChannelAccess.PUBLIC || ownedBy(player)
                || access == ChannelAccess.SECURED && station != null && stations.grants(station, player);
    }
}
