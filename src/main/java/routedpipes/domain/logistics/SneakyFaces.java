package routedpipes.domain.logistics;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import routedpipes.domain.item.ItemType;
import routedpipes.domain.routing.Side;

// the faces of an inventory a pipe reaches through instead of the one it touches
// single is the face of one sneaky upgrade, combined the faces of the row a combination upgrade unlocks
public record SneakyFaces(Optional<Side> single, List<Side> combined) {

    public static final SneakyFaces NONE = new SneakyFaces(Optional.empty(), List.of());

    // an upgrade slot of the pipe as far as faces go
    public record Slot(Optional<Side> sneaky, boolean combination) {

        public static final Slot OTHER = new Slot(Optional.empty(), false);
        public static final Slot COMBINATION = new Slot(Optional.empty(), true);

        public static Slot sneaky(Side face) {
            return new Slot(Optional.of(face), false);
        }
    }

    // a sink module and the face of the sneaky upgrade in its own slots
    public record ModuleFace(SinkRule rule, Optional<Side> sneaky) {
    }

    public SneakyFaces {
        combined = List.copyOf(combined);
    }

    // of a sneaky and a combination upgrade in the same pipe the one in the earlier slot counts
    public static SneakyFaces of(List<Slot> slots, List<Side> row) {
        for (Slot slot : slots) {
            if (slot.sneaky().isPresent()) {
                return new SneakyFaces(slot.sneaky(), List.of());
            }
            if (slot.combination()) {
                return new SneakyFaces(Optional.empty(), row);
            }
        }
        return NONE;
    }

    // a module with a sneaky upgrade of its own goes by that one and nothing else
    public SneakyFaces forModule(Optional<Side> own) {
        return own.isPresent() ? new SneakyFaces(own, List.of()) : this;
    }

    // the face the inventory is looked into and emptied through, the row is for arriving items only
    public Side reading(Side touching) {
        return single.orElse(touching);
    }

    // the faces an arriving item tries, in this order
    public List<Side> inserting(Side touching) {
        if (single.isPresent()) {
            return List.of(single.get());
        }
        return combined.isEmpty() ? List.of(touching) : combined;
    }

    // the sneaky face of the module that takes the item: the keenest one, the earlier slot if two are equally keen
    // empty when no module wants the item or the one that does has no sneaky upgrade
    public static Optional<Side> ofModuleTaking(ItemType type, Map<ItemType, Integer> contents, List<ModuleFace> modules) {
        ModuleFace winner = null;
        int best = Integer.MIN_VALUE;
        for (ModuleFace module : modules) {
            Optional<SinkBid> bid = module.rule().bid(type, contents);
            if (bid.isPresent() && (winner == null || bid.get().priority() > best)) {
                winner = module;
                best = bid.get().priority();
            }
        }
        return winner == null ? Optional.empty() : winner.sneaky();
    }
}
