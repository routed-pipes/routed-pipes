package logisticspipes.adapter.computercraft;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dan200.computercraft.api.peripheral.IComputerAccess;
import logisticspipes.application.port.ComputerEvents;
import logisticspipes.domain.routing.RouterId;

// which computers sit at which routed pipe right now, so that events find them
// computers come and go on their own threads
public final class AttachedComputers implements ComputerEvents {

    public static final AttachedComputers INSTANCE = new AttachedComputers();

    private final Map<RouterId, Set<IComputerAccess>> computers = new ConcurrentHashMap<>();

    private AttachedComputers() {
    }

    void attach(RouterId pipe, IComputerAccess computer) {
        computers.computeIfAbsent(pipe, key -> ConcurrentHashMap.newKeySet()).add(computer);
    }

    void detach(RouterId pipe, IComputerAccess computer) {
        computers.computeIfPresent(pipe, (key, attached) -> {
            attached.remove(computer);
            return attached.isEmpty() ? null : attached;
        });
    }

    @Override
    public void queue(RouterId pipe, OptionalInt computer, String event, Object... arguments) {
        for (IComputerAccess attached : computers.getOrDefault(pipe, Set.of())) {
            if (computer.isEmpty() || computer.getAsInt() == attached.getID()) {
                attached.queueEvent(event, arguments);
            }
        }
    }
}
