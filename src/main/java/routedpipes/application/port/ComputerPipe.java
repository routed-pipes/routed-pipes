package routedpipes.application.port;

import java.util.List;
import java.util.Optional;

import routedpipes.application.computer.ModuleInfo;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.security.Access;

// what the commands of a computer need from a routed pipe beyond what the network knows about it
public interface ComputerPipe {

    RouterId id();

    RouterNode node();

    long now();

    // what is built into the pipe, for the pipes that work like a module
    Optional<ModuleInfo> module();

    // crafting pipes only: takes over the recipe of the crafting table next to the pipe
    void importRecipe();

    // chassis only: what is in each module slot, in the order of the slots
    List<Optional<ModuleInfo>> modules();

    // what the security station the pipe is locked to says to the computer with that id
    Access computerAccess(int computer);

    // whether the pipe has the upgrade that lets its computer get at other pipes
    boolean remoteControl();

    // another routed pipe by its id, empty while it is not loaded
    Optional<ComputerPipe> pipe(RouterId other);
}
