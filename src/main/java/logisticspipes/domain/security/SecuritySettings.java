package logisticspipes.domain.security;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// what one security station lets players do with the pipes that are locked to it
// the owner may do everything, everyone else only what was switched on for them
public final class SecuritySettings {

    // energy in FE one look into the list costs, the original mod's 10 units at its rate of 2 RF per unit
    public static final int CHECK_ENERGY = 20;

    private UUID owner;
    // cards of a station that is not authorized fall out of their pipes
    private boolean authorized = true;
    // whether explosions and machines may remove locked pipes
    private boolean automatedRemoval;
    private final Map<UUID, Set<Permission>> players = new LinkedHashMap<>();

    // empty for a station nobody has claimed yet
    public Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    public void setOwner(UUID player) {
        owner = player;
    }

    public boolean isOwner(UUID player) {
        return player.equals(owner);
    }

    public boolean authorized() {
        return authorized;
    }

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
    }

    public boolean automatedRemoval() {
        return automatedRemoval;
    }

    public void setAutomatedRemoval(boolean allowed) {
        automatedRemoval = allowed;
    }

    // the players on the list, in the order they were added
    public List<UUID> players() {
        return List.copyOf(players.keySet());
    }

    // puts a player on the list, with nothing allowed yet
    public void add(UUID player) {
        players.computeIfAbsent(player, key -> EnumSet.noneOf(Permission.class));
    }

    public Set<Permission> permissions(UUID player) {
        Set<Permission> granted = players.get(player);
        return granted == null ? Set.of() : Set.copyOf(granted);
    }

    public void set(UUID player, Permission permission, boolean allowed) {
        add(player);
        if (allowed) {
            players.get(player).add(permission);
        } else {
            players.get(player).remove(permission);
        }
    }

    // the owner is never looked up, so it costs them nothing and works without energy
    public Access check(UUID player, Permission permission, EnergySource energy) {
        if (isOwner(player)) {
            return Access.ALLOWED;
        }
        if (!energy.use(CHECK_ENERGY)) {
            return Access.NO_ENERGY;
        }
        return permissions(player).contains(permission) ? Access.ALLOWED : Access.DENIED;
    }

    // for a pipe that is about to be removed by something that is not a player
    public Access checkAutomatedRemoval(EnergySource energy) {
        if (!energy.use(CHECK_ENERGY)) {
            return Access.NO_ENERGY;
        }
        return automatedRemoval ? Access.ALLOWED : Access.DENIED;
    }
}
