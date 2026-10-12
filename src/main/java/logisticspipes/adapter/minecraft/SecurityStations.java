package logisticspipes.adapter.minecraft;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import logisticspipes.domain.security.Permission;
import logisticspipes.domain.security.SecuritySettings;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

// the settings of every security station, kept with the world and not with the block
// a locked pipe can ask about its station that way even when the chunk of the station is not loaded
public final class SecurityStations extends SavedData {

    private static final String FILE = "logisticspipes_security";

    // the names are what the screens show for the players on the list
    public static final class Station {
        private final SecuritySettings settings = new SecuritySettings();
        private final Map<UUID, String> names = new HashMap<>();

        public SecuritySettings settings() {
            return settings;
        }

        public String name(UUID player) {
            return names.getOrDefault(player, player.toString());
        }

        public void add(UUID player, String name) {
            settings.add(player);
            names.put(player, name);
        }
    }

    private final Map<UUID, Station> stations = new HashMap<>();

    // server side only
    public static SecurityStations of(Level level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(new Factory<>(SecurityStations::new, SecurityStations::load, null), FILE);
    }

    public Optional<Station> find(UUID id) {
        return Optional.ofNullable(stations.get(id));
    }

    // the station with this id, a new one if the world does not know it yet
    public Station station(UUID id) {
        return stations.computeIfAbsent(id, key -> {
            setDirty();
            return new Station();
        });
    }

    public void remove(UUID id) {
        if (stations.remove(id) != null) {
            setDirty();
        }
    }

    // whether the station still exists and vouches for its cards
    public boolean authorized(UUID id) {
        return find(id).filter(station -> station.settings().authorized()).isPresent();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        stations.forEach((id, station) -> {
            SecuritySettings settings = station.settings();
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            settings.owner().ifPresent(owner -> entry.putUUID("Owner", owner));
            entry.putBoolean("Authorized", settings.authorized());
            entry.putBoolean("AutomatedRemoval", settings.automatedRemoval());
            entry.putBoolean("ComputersAllowed", settings.computersAllowed());
            entry.putIntArray("ComputerExceptions", settings.computerExceptions());
            ListTag players = new ListTag();
            for (UUID player : settings.players()) {
                CompoundTag saved = new CompoundTag();
                saved.putUUID("Id", player);
                saved.putString("Name", station.name(player));
                ListTag permissions = new ListTag();
                settings.permissions(player).forEach(permission -> permissions.add(StringTag.valueOf(permission.name())));
                saved.put("Permissions", permissions);
                players.add(saved);
            }
            entry.put("Players", players);
            list.add(entry);
        });
        tag.put("Stations", list);
        return tag;
    }

    public static SecurityStations load(CompoundTag tag, HolderLookup.Provider registries) {
        SecurityStations loaded = new SecurityStations();
        ListTag list = tag.getList("Stations", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            Station station = new Station();
            SecuritySettings settings = station.settings();
            if (entry.hasUUID("Owner")) {
                settings.setOwner(entry.getUUID("Owner"));
            }
            settings.setAuthorized(entry.getBoolean("Authorized"));
            settings.setAutomatedRemoval(entry.getBoolean("AutomatedRemoval"));
            settings.setComputersAllowed(entry.getBoolean("ComputersAllowed"));
            for (int computer : entry.getIntArray("ComputerExceptions")) {
                settings.addComputerException(computer);
            }
            ListTag players = entry.getList("Players", Tag.TAG_COMPOUND);
            for (int player = 0; player < players.size(); player++) {
                CompoundTag saved = players.getCompound(player);
                UUID id = saved.getUUID("Id");
                station.add(id, saved.getString("Name"));
                ListTag permissions = saved.getList("Permissions", Tag.TAG_STRING);
                for (int permission = 0; permission < permissions.size(); permission++) {
                    permissionNamed(permissions.getString(permission)).ifPresent(known -> settings.set(id, known, true));
                }
            }
            loaded.stations.put(entry.getUUID("Id"), station);
        }
        return loaded;
    }

    // empty for a permission this version does not know
    private static Optional<Permission> permissionNamed(String name) {
        for (Permission permission : Permission.values()) {
            if (permission.name().equals(name)) {
                return Optional.of(permission);
            }
        }
        return Optional.empty();
    }
}
