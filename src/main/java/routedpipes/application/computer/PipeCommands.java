package routedpipes.application.computer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import routedpipes.application.LogisticsNetwork;
import routedpipes.application.RequestOutcome;
import routedpipes.application.port.ComputerEvents;
import routedpipes.application.port.ComputerPipe;
import routedpipes.application.port.ItemTables;
import routedpipes.domain.crafting.CraftingSetup;
import routedpipes.domain.crafting.OutputOrder;
import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.RouterId;

// what a computer can do with a routed pipe, under the names the original mod's peripheral had
// results are strings, numbers, booleans, lists and maps, so that they turn into plain tables in a script
public final class PipeCommands {

    public static final String NORMAL = "RoutedPipes:Normal";
    public static final String REQUEST = "RoutedPipes:Request";
    public static final String CRAFTING = "RoutedPipes:Crafting";
    public static final String CHASSIS = "LogisticsChassiePipe";

    public static final String MESSAGE_EVENT = "LP_MESSAGE";
    public static final String BROADCAST_EVENT = "LP_BROADCAST";

    public static final String COUNT = "count";

    public static final String DONE = "DONE";
    public static final String MISSING = "MISSING";
    public static final String NO_POWER = "NO_POWER";
    // energy in FE a request from a computer costs before anything moves, the original mod's 15 units at 2 RF per unit
    public static final int REQUEST_ENERGY = 30;

    @FunctionalInterface
    private interface Body {
        Object[] run(Caller caller, List<Object> arguments);
    }

    // direct ones are only for a computer that sits at the pipe itself
    // the security station of the pipe is asked before every command that is not open
    private record Command(String parameters, boolean direct, boolean open, String description, Body body) {
    }

    public static final String DENIED = "Permission denied";

    private final LogisticsNetwork network;
    private final ComputerPipe pipe;
    private final ItemTables items;
    private final ComputerEvents events;
    private final Map<String, Command> commands = new LinkedHashMap<>();

    public PipeCommands(LogisticsNetwork network, ComputerPipe pipe, ItemTables items, ComputerEvents events) {
        this.network = network;
        this.pipe = pipe;
        this.items = items;
        this.events = events;
        open("getType", "Returns the type of this pipe", (caller, arguments) -> one(type()));
        open("help", "Lists the commands of this pipe", (caller, arguments) -> one(help(caller)));
        open("canAccess", "Returns true if the computer is allowed to interact with the connected pipe.",
                (caller, arguments) -> one(pipe.computerAccess(caller.computer()).allowed()));
        add("getRouterUUID", "", false, "Returns the Router UUID; all pipes have a unique ID (lifetime stable)",
                (caller, arguments) -> one(pipe.id().value().toString()));
        add("hasLogisticsModule", "", false, "Returns true if the pipe has an internal module",
                (caller, arguments) -> one(pipe.module().isPresent()));
        add("getLogisticsModule", "", false, "Returns the Internal LogisticsModule for this pipe",
                (caller, arguments) -> one(pipe.module().map(this::table).orElse(null)));
        add("sendMessage", "number computerId, any message", true,
                "Sends a message to the given computerId over the LP network. Event: " + MESSAGE_EVENT,
                (caller, arguments) -> send(caller, OptionalInt.of(number(arguments, 0)), MESSAGE_EVENT, argument(arguments, 1)));
        add("sendBroadcast", "any message", true,
                "Sends a broadcast message to all Computer connected to this LP network. Event: " + BROADCAST_EVENT,
                (caller, arguments) -> send(caller, OptionalInt.empty(), BROADCAST_EVENT, argument(arguments, 0)));
        add("getPipeForUUID", "string uuid", true, "Returns the access to the pipe of the given router UUID",
                (caller, arguments) -> one(remote(argument(arguments, 0))));
        switch (type()) {
            case REQUEST -> addRequesting();
            case CRAFTING -> addCrafting();
            case CHASSIS -> addChassis();
            default -> { }
        }
    }

    private void addRequesting() {
        add("makeRequest", "item, number amount [, boolean forceCrafting]", false,
                "Requests the given item with the given amount, without an amount the count in the item table is taken",
                (caller, arguments) -> request(arguments));
        add("getAvailableItems", "", false, "Asks for all available items inside the Logistics Network",
                (caller, arguments) -> one(network.available(pipe.id()).entrySet().stream().filter(entry -> entry.getValue() > 0)
                        .map(entry -> table(new ItemBatch(entry.getKey(), entry.getValue()))).toList()));
        add("getCraftableItems", "", false, "Asks for all craftable items inside the Logistics Network",
                (caller, arguments) -> one(network.craftable(pipe.id()).stream().map(items::describe).toList()));
        add("getItemAmount", "item", false, "Asks for the amount of an item inside the Logistics Network",
                (caller, arguments) -> one(network.available(pipe.id()).getOrDefault(item(argument(arguments, 0)), 0)));
    }

    private void addCrafting() {
        add("reimport", "", false, "Imports the crafting recipe from the connected machine/crafter", (caller, arguments) -> {
            pipe.importRecipe();
            return none();
        });
        add("getRecipe", "", false, "Returns what this pipe crafts and out of what",
                (caller, arguments) -> one(pipe.node().crafts().stream().findFirst().map(this::table).orElse(null)));
        add("getOrders", "", false, "Returns the crafted items this pipe still owes",
                (caller, arguments) -> one(pipe.node().outputOrders().all().stream().map(this::table).toList()));
    }

    private void addChassis() {
        add("getModuleInSlot", "number slot", false, "Returns the LogisticsModule for the given slot number starting by 1",
                (caller, arguments) -> {
                    List<Optional<ModuleInfo>> modules = pipe.modules();
                    int slot = number(arguments, 0);
                    if (slot < 1 || slot > modules.size()) {
                        throw new ComputerException("Slot out of Inventory");
                    }
                    return one(modules.get(slot - 1).map(this::table).orElse(null));
                });
        add("getChassieSize", "", false, "Returns the size of this Chassis pipe", (caller, arguments) -> one(pipe.modules().size()));
    }

    private void add(String name, String parameters, boolean direct, String description, Body body) {
        commands.put(name, new Command(parameters, direct, false, description, body));
    }

    private void open(String name, String description, Body body) {
        commands.put(name, new Command("", false, true, description, body));
    }

    // the name scripts find the pipe under
    public String type() {
        PipeKind kind = pipe.node().kind();
        if (kind.requestPipe() || kind == PipeKind.REQUEST_TABLE) {
            return REQUEST;
        }
        return switch (kind) {
            case CRAFTING -> CRAFTING;
            case CHASSIS -> CHASSIS;
            default -> NORMAL;
        };
    }

    // what the caller can run, a computer further away gets fewer
    public List<String> names(boolean direct) {
        return commands.entrySet().stream().filter(entry -> direct || !entry.getValue().direct()).map(Map.Entry::getKey).toList();
    }

    public Object[] call(String name, Caller caller, List<Object> arguments) {
        Command command = commands.get(name);
        if (command == null) {
            throw new ComputerException("No such method: " + name);
        }
        if (command.direct() && !caller.direct()) {
            throw new ComputerException(DENIED);
        }
        if (!command.open() && !pipe.computerAccess(caller.computer()).allowed()) {
            throw new ComputerException(DENIED);
        }
        return command.body().run(caller, arguments);
    }

    private static Object[] one(Object value) {
        return new Object[] {value};
    }

    private static Object[] none() {
        return new Object[0];
    }

    private static Object argument(List<Object> arguments, int index) {
        return index < arguments.size() ? arguments.get(index) : null;
    }

    // scripts only have one kind of number, what is behind the point is dropped
    private static int number(List<Object> arguments, int index) {
        if (argument(arguments, index) instanceof Number number) {
            return (int) Math.floor(number.doubleValue());
        }
        throw new ComputerException("Argument " + (index + 1) + " has to be a number");
    }

    // an item is given by its name or as a table like the ones the commands hand out
    // data only tells items apart that the network knows, so a table with data has to be one of those
    private ItemType item(Object given) {
        Optional<ItemType> found = Optional.empty();
        if (given instanceof String name) {
            found = items.named(name);
        } else if (given instanceof Map<?, ?> table && table.get(ItemTables.NAME) instanceof String name) {
            Object data = table.get(ItemTables.DATA);
            if (data == null) {
                found = items.named(name);
            } else {
                Set<ItemType> known = new LinkedHashSet<>(network.available(pipe.id()).keySet());
                known.addAll(network.craftable(pipe.id()));
                found = known.stream().filter(type -> {
                    Map<String, Object> described = items.describe(type);
                    return name.equals(described.get(ItemTables.NAME)) && data.equals(described.get(ItemTables.DATA));
                }).findFirst();
            }
        }
        return found.orElseThrow(() -> new ComputerException("Invalid ItemIdentifier"));
    }

    // all of it or nothing, like an order a player places
    private Object[] request(List<Object> arguments) {
        ItemType type = item(argument(arguments, 0));
        boolean counted = argument(arguments, 1) instanceof Number;
        int amount;
        if (counted) {
            amount = number(arguments, 1);
        } else if (argument(arguments, 0) instanceof Map<?, ?> table && table.get(COUNT) instanceof Number count) {
            amount = (int) Math.floor(count.doubleValue());
        } else {
            throw new ComputerException("Argument 2 has to be a number");
        }
        if (amount < 1) {
            throw new ComputerException("The amount has to be at least 1");
        }
        boolean forceCrafting = Boolean.TRUE.equals(argument(arguments, counted ? 2 : 1));
        if (!network.usePower(pipe.id(), REQUEST_ENERGY)) {
            return one(NO_POWER);
        }
        ItemBatch wanted = new ItemBatch(type, amount);
        RequestOutcome outcome = forceCrafting ? network.requestCrafted(pipe.id(), wanted, pipe.now())
                : network.request(pipe.id(), wanted, pipe.now());
        if (outcome.started()) {
            return new Object[] {DONE, List.of(table(wanted))};
        }
        if (!outcome.powered()) {
            return one(NO_POWER);
        }
        return new Object[] {MISSING, outcome.missing().entrySet().stream()
                .map(entry -> table(new ItemBatch(entry.getKey(), entry.getValue()))).toList()};
    }

    private String help(Caller caller) {
        StringBuilder text = new StringBuilder("Type: ").append(type());
        for (String name : names(caller.direct())) {
            Command command = commands.get(name);
            text.append('\n').append(name).append('(').append(command.parameters()).append("): ").append(command.description());
        }
        return text.toString();
    }

    // the commands of another pipe of the network, null if there is none with that id
    // what comes back is for a caller that is not at that pipe
    private PipeCommands remote(Object given) {
        if (!pipe.remoteControl()) {
            throw new ComputerException(DENIED + ", this pipe needs a CC Remote Control Upgrade");
        }
        RouterId other;
        try {
            other = new RouterId(UUID.fromString(String.valueOf(given)));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!other.equals(pipe.id()) && !network.reachable(pipe.id()).contains(other)) {
            return null;
        }
        return pipe.pipe(other).map(found -> new PipeCommands(network, found, items, events)).orElse(null);
    }

    // to this pipe and every pipe it reaches
    private Object[] send(Caller caller, OptionalInt computer, String event, Object message) {
        List<RouterId> pipes = new ArrayList<>(network.reachable(pipe.id()));
        pipes.add(0, pipe.id());
        for (RouterId target : pipes) {
            events.queue(target, computer, event, caller.computer(), message);
        }
        return none();
    }

    private Map<String, Object> table(ItemBatch batch) {
        Map<String, Object> table = new LinkedHashMap<>(items.describe(batch.type()));
        table.put(COUNT, batch.count());
        return table;
    }

    private Map<String, Object> table(ModuleInfo module) {
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("type", module.type());
        table.put("kind", module.kind());
        table.put("hasGui", module.hasGui());
        module.filter().ifPresent(filter -> table.put("filter", filter.stream().map(this::table).toList()));
        module.defaultRoute().ifPresent(enabled -> table.put("defaultRoute", enabled));
        return table;
    }

    private Map<String, Object> table(CraftingSetup craft) {
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("output", table(craft.recipe().output()));
        table.put("inputs", craft.recipe().inputs().stream().map(this::table).toList());
        table.put("priority", craft.priority());
        craft.byproduct().ifPresent(extra -> table.put("byproduct", table(extra)));
        return table;
    }

    // an order without a destination is something extra the machine put out
    private Map<String, Object> table(OutputOrder order) {
        Map<String, Object> table = table(order.batch());
        if (!order.isExtra()) {
            table.put("destination", order.destination().value().toString());
        }
        return table;
    }
}
