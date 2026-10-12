package logisticspipes.domain.sign;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Side;

// the signs of one pipe, a face holds at most one
public final class SignFaces {

    private static final List<Side> AROUND = List.of(Side.NORTH, Side.SOUTH, Side.WEST, Side.EAST);

    private final Map<Side, SignKind> signs = new EnumMap<>(Side.class);

    // false when the face is taken or the pipe is not one for this kind of sign
    public boolean place(Side face, SignKind kind, PipeKind pipe) {
        if (signs.containsKey(face) || !kind.allowedOn(pipe)) {
            return false;
        }
        signs.put(face, kind);
        return true;
    }

    public Optional<SignKind> remove(Side face) {
        return Optional.ofNullable(signs.remove(face));
    }

    public Optional<SignKind> at(Side face) {
        return Optional.ofNullable(signs.get(face));
    }

    public Map<Side, SignKind> all() {
        return Collections.unmodifiableMap(signs);
    }

    public boolean isEmpty() {
        return signs.isEmpty();
    }

    public boolean has(SignKind kind) {
        return signs.containsValue(kind);
    }

    public void clear() {
        signs.clear();
    }

    // signs around a pipe are a block wide and would run into each other at the corners, so they move outwards
    // two on opposite faces are the only ones that have room
    public boolean pushedOut(Side face) {
        if (!AROUND.contains(face) || !signs.containsKey(face)) {
            return false;
        }
        long around = AROUND.stream().filter(signs::containsKey).count();
        return around > 2 || around == 2 && !signs.containsKey(face.opposite());
    }
}
