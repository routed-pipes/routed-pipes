package routedpipes.adapter.minecraft;

import java.util.Optional;
import java.util.UUID;

import routedpipes.domain.security.Access;
import routedpipes.domain.security.Permission;
import routedpipes.domain.security.PipeLock;
import routedpipes.domain.security.SecuritySettings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// the security card of a routed pipe: how it gets in, when it falls out and whom it keeps away from the pipe
public final class PipeSecurity {

    private final SimpleContainer card = new SimpleContainer(1);
    private final PipeLock lock = new PipeLock();
    private final PipeBlockEntity pipe;
    // read off the card whenever it changes, every tick of a locked pipe asks for it
    private Optional<UUID> station = Optional.empty();
    // set when the card or the pipes around may have changed, the card is handed on in the next tick
    private boolean spreadPending = true;

    PipeSecurity(PipeBlockEntity pipe) {
        this.pipe = pipe;
        card.addListener(container -> cardChanged());
    }

    private void cardChanged() {
        station = SecurityCardItem.stationOf(card.getItem(0));
        spreadPending = true;
        pipe.setChanged();
        Level level = pipe.getLevel();
        if (level != null && !level.isClientSide) {
            // pipes of another station are no neighbours any more, or are again
            pipe.markLinksDirty();
            PipeTopology.markRoutersDirty(level, pipe.getBlockPos());
        }
    }

    void linksChanged() {
        spreadPending = true;
    }

    public Container card() {
        return card;
    }

    // the station this pipe is locked to
    public Optional<UUID> station() {
        return station;
    }

    // server side only
    public boolean authorized() {
        return station().filter(id -> SecurityStations.of(pipe.getLevel()).authorized(id)).isPresent();
    }

    // only a card whose station vouches for it goes in, and only into a pipe without one
    public boolean accepts(ItemStack stack) {
        return card.isEmpty()
                && SecurityCardItem.stationOf(stack).filter(id -> SecurityStations.of(pipe.getLevel()).authorized(id)).isPresent();
    }

    // moves one card from the stack into the pipe
    boolean insert(ItemStack held) {
        if (!accepts(held)) {
            return false;
        }
        card.setItem(0, held.split(1));
        return true;
    }

    void tick() {
        if (station.isEmpty()) {
            return;
        }
        boolean authorized = SecurityStations.of(pipe.getLevel()).authorized(station.get());
        if (lock.expired(authorized)) {
            card.clearContent();
        } else if (authorized && spreadPending) {
            // one card locks everything connected to it, a pipe further each tick
            spreadPending = false;
            for (PipeBlockEntity neighbour : PipeTopology.neighbours(pipe.getLevel(), pipe.getBlockPos())) {
                if (neighbour.security().card.isEmpty()) {
                    neighbour.security().card.setItem(0, card.getItem(0).copyWithCount(1));
                }
            }
        }
    }

    // without a card nothing is restricted, and neither with the card of a station that is gone
    private Optional<SecuritySettings> settings() {
        return station().flatMap(id -> SecurityStations.of(pipe.getLevel()).find(id)).map(SecurityStations.Station::settings);
    }

    // what the station of this pipe says to the player doing something, the energy comes from the network of the pipe
    public Access access(Player player, Permission permission) {
        return settings().map(settings -> settings.check(player.getUUID(), permission, pipe::usePower)).orElse(Access.ALLOWED);
    }

    // like access, and tells the player when the answer is no
    public boolean allows(Player player, Permission permission) {
        return told(player, access(player, permission));
    }

    // what the station of this pipe says to a computer, -1 is one that is not at the pipe itself
    public Access computerAccess(int computer) {
        return settings().map(settings -> settings.checkComputer(computer, pipe::usePower)).orElse(Access.ALLOWED);
    }

    // for explosions and machines
    public boolean allowsAutomatedRemoval() {
        return settings().map(settings -> settings.checkAutomatedRemoval(pipe::usePower)).orElse(Access.ALLOWED).allowed();
    }

    static boolean told(Player player, Access access) {
        if (!access.allowed()) {
            player.displayClientMessage(Component.translatable(access == Access.NO_ENERGY
                    ? "routedpipes.security.no_energy" : "routedpipes.security.denied").withStyle(ChatFormatting.RED), false);
        }
        return access.allowed();
    }

    CompoundTag save(HolderLookup.Provider registries) {
        return ContainerTags.save(card, registries);
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        ContainerTags.load(card, tag, registries);
    }
}
