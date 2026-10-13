package routedpipes.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import routedpipes.application.computer.ModuleInfo;
import routedpipes.application.port.ComputerEvents;
import routedpipes.application.port.ComputerPipe;
import routedpipes.application.port.ItemTables;
import routedpipes.application.port.RouterNode;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.security.Access;

// a routed pipe as the commands of a computer see it, everything a test wants to change is a field
public final class FakeComputerPipe implements ComputerPipe {

    // items are tables with their name, a trait item also has data named after its tags
    public static final ItemTables ITEMS = new ItemTables() {
        @Override
        public Map<String, Object> describe(ItemType type) {
            Map<String, Object> table = new LinkedHashMap<>();
            if (type instanceof Fixtures.TraitItem item) {
                table.put(NAME, item.name());
                table.put(DATA, String.join(",", item.tags()));
            } else {
                table.put(NAME, ((Fixtures.TestItem) type).name());
            }
            return table;
        }

        @Override
        public Optional<ItemType> named(String name) {
            return name.startsWith("unknown") ? Optional.empty() : Optional.of(new Fixtures.TestItem(name));
        }
    };

    public record Event(RouterId pipe, OptionalInt computer, String event, List<Object> arguments) {
    }

    // keeps what was queued
    public static final class Events implements ComputerEvents {
        public final List<Event> queued = new ArrayList<>();

        @Override
        public void queue(RouterId pipe, OptionalInt computer, String event, Object... arguments) {
            queued.add(new Event(pipe, computer, event, List.of(arguments)));
        }
    }

    private final RouterId id;
    private final RouterNode node;
    public long now = 100;
    public ModuleInfo module;
    public int recipeImports;
    public List<Optional<ModuleInfo>> modules = List.of();
    // the computers the security station lets in, null for a pipe that is not locked
    public Set<Integer> allowedComputers;
    public int accessChecks;
    public boolean remoteControl;
    public final Map<RouterId, ComputerPipe> others = new LinkedHashMap<>();

    public FakeComputerPipe(RouterId id, RouterNode node) {
        this.id = id;
        this.node = node;
    }

    @Override
    public RouterId id() {
        return id;
    }

    @Override
    public RouterNode node() {
        return node;
    }

    @Override
    public long now() {
        return now;
    }

    @Override
    public Optional<ModuleInfo> module() {
        return Optional.ofNullable(module);
    }

    @Override
    public void importRecipe() {
        recipeImports++;
    }

    @Override
    public List<Optional<ModuleInfo>> modules() {
        return modules;
    }

    @Override
    public Access computerAccess(int computer) {
        accessChecks++;
        return allowedComputers == null || allowedComputers.contains(computer) ? Access.ALLOWED : Access.DENIED;
    }

    @Override
    public boolean remoteControl() {
        return remoteControl;
    }

    @Override
    public Optional<ComputerPipe> pipe(RouterId other) {
        return Optional.ofNullable(others.get(other));
    }
}
