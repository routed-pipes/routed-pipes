package logisticspipes.adapter.minecraft;

import java.util.UUID;

import logisticspipes.domain.channel.Channel;
import logisticspipes.domain.channel.ChannelAccess;
import logisticspipes.domain.channel.Channels;
import logisticspipes.domain.channel.StationAccess;
import logisticspipes.domain.security.Permission;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

// the channels of inventory system connectors, kept with the world
public final class ChannelRegistry extends SavedData {

    private static final String FILE = "logisticspipes_channels";

    private final Channels channels = new Channels();

    // server side only
    public static ChannelRegistry of(Level level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(new Factory<>(ChannelRegistry::new, ChannelRegistry::load, null), FILE);
    }

    // call setDirty after changing them
    public Channels channels() {
        return channels;
    }

    // a station lets its owner and the players it gave the permission use its channels, looking that up costs nothing
    public static StationAccess stations(Level level) {
        return (station, player) -> SecurityStations.of(level).find(station).map(SecurityStations.Station::settings)
                .filter(settings -> settings.isOwner(player)
                        || settings.permissions(player).contains(Permission.ACCESS_ROUTING_CHANNELS))
                .isPresent();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Channel channel : channels.all()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", channel.id());
            entry.putString("Name", channel.name());
            entry.putUUID("Owner", channel.owner());
            entry.putString("OwnerName", channel.ownerName());
            entry.putString("Access", channel.access().name());
            if (channel.station() != null) {
                entry.putUUID("Station", channel.station());
            }
            list.add(entry);
        }
        tag.put("Channels", list);
        return tag;
    }

    public static ChannelRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        ChannelRegistry loaded = new ChannelRegistry();
        ListTag list = tag.getList("Channels", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            UUID station = entry.hasUUID("Station") ? entry.getUUID("Station") : null;
            loaded.channels.put(new Channel(entry.getUUID("Id"), entry.getString("Name"), entry.getUUID("Owner"),
                    entry.getString("OwnerName"), accessNamed(entry.getString("Access"), station), station));
        }
        return loaded;
    }

    // a kind of access this version does not know is treated as the strictest
    private static ChannelAccess accessNamed(String name, UUID station) {
        for (ChannelAccess access : ChannelAccess.values()) {
            if (access.name().equals(name) && (access != ChannelAccess.SECURED || station != null)) {
                return access;
            }
        }
        return ChannelAccess.PRIVATE;
    }
}
