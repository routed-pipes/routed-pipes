package routedpipes.domain.channel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// every channel of a world, only its owner may change or delete one
public final class Channels {

    public static final int MAX_NAME = 32;
    // channels are world data made from what clients send, so one player cannot make endless ones
    public static final int MAX_PER_OWNER = 32;

    private final Map<UUID, Channel> channels = new LinkedHashMap<>();

    // in the order they were made
    public List<Channel> all() {
        return List.copyOf(channels.values());
    }

    public Optional<Channel> find(UUID id) {
        return Optional.ofNullable(channels.get(id));
    }

    // for channels read from a save
    public void put(Channel channel) {
        channels.put(channel.id(), channel);
    }

    public List<Channel> allowedFor(UUID player, StationAccess stations) {
        return channels.values().stream().filter(channel -> channel.allows(player, stations)).toList();
    }

    private static String cleaned(String name) {
        String stripped = name.strip();
        return stripped.length() > MAX_NAME ? stripped.substring(0, MAX_NAME) : stripped;
    }

    // without a station there is nothing to secure a channel with, so it becomes private
    private static ChannelAccess possible(ChannelAccess access, UUID station) {
        return access == ChannelAccess.SECURED && station == null ? ChannelAccess.PRIVATE : access;
    }

    // empty when the name is blank or the player has made as many as they may
    public Optional<Channel> create(String name, UUID owner, String ownerName, ChannelAccess access, UUID station) {
        if (cleaned(name).isEmpty() || channels.values().stream().filter(channel -> channel.ownedBy(owner)).count() >= MAX_PER_OWNER) {
            return Optional.empty();
        }
        ChannelAccess granted = possible(access, station);
        Channel channel = new Channel(UUID.randomUUID(), cleaned(name), owner, ownerName, granted,
                granted == ChannelAccess.SECURED ? station : null);
        channels.put(channel.id(), channel);
        return Optional.of(channel);
    }

    private Optional<Channel> owned(UUID id, UUID player) {
        return find(id).filter(channel -> channel.ownedBy(player));
    }

    // the three below return whether the player was allowed to and something changed
    public boolean rename(UUID id, UUID player, String name) {
        Optional<Channel> channel = owned(id, player)
                .filter(found -> !cleaned(name).isEmpty() && !found.name().equals(cleaned(name)));
        channel.ifPresent(found -> channels.put(id, new Channel(id, cleaned(name), found.owner(), found.ownerName(),
                found.access(), found.station())));
        return channel.isPresent();
    }

    public boolean setAccess(UUID id, UUID player, ChannelAccess access, UUID station) {
        ChannelAccess granted = possible(access, station);
        Optional<Channel> channel = owned(id, player).filter(found -> found.access() != granted);
        channel.ifPresent(found -> channels.put(id, new Channel(id, found.name(), found.owner(), found.ownerName(), granted,
                granted == ChannelAccess.SECURED ? station : null)));
        return channel.isPresent();
    }

    public boolean delete(UUID id, UUID player) {
        return owned(id, player).isPresent() && channels.remove(id) != null;
    }
}
