package routedpipes.application.port;

import java.util.OptionalInt;

import routedpipes.domain.routing.RouterId;

// events for the computers that sit at routed pipes
@FunctionalInterface
public interface ComputerEvents {

    // to the computer with that id at the pipe, or to all of them
    void queue(RouterId pipe, OptionalInt computer, String event, Object... arguments);
}
