package routedpipes.domain.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import routedpipes.domain.hud.HudElement.Entry;
import routedpipes.domain.hud.HudElement.Grid;
import routedpipes.domain.hud.HudElement.Label;
import routedpipes.domain.hud.HudElement.Modules;
import routedpipes.domain.item.ItemBatch;

// decides whether a pipe has something to show and what its panel says
public final class HudPanels {

    // longer lists are cut, a panel has to stay small enough to send often
    public static final int MAX_LISTED = 48;
    // a connector that just handed everything over keeps its panel a little longer, two seconds
    public static final int CONNECTOR_LINGER = 40;

    private static final String KEY = "routedpipes.hud.";

    private HudPanels() {
    }

    public static Optional<HudPanel> of(HudSource source) {
        return Optional.ofNullable(switch (source) {
            case HudSource.Chassis chassis -> chassis(chassis);
            case HudSource.Basic basic -> basic(basic);
            case HudSource.Provider provider -> provider(provider);
            case HudSource.Crafting crafting -> crafting(crafting);
            case HudSource.Satellite satellite -> satellite(satellite);
            case HudSource.Supplier supplier -> supplier(supplier);
            case HudSource.Connector connector -> connector(connector);
            case HudSource.Power power -> power(power);
        });
    }

    private static Label label(String name, Object... args) {
        return new Label(HudText.of(KEY + name, args), false);
    }

    private static Grid grid(List<ItemBatch> stacks, int columns, int perPage) {
        return new Grid(stacks.stream().limit(MAX_LISTED).toList(), columns, perPage);
    }

    private static HudPanel chassis(HudSource.Chassis chassis) {
        if (chassis.modules().isEmpty()) {
            return null;
        }
        List<HudElement.Module> modules = new ArrayList<>();
        for (HudSource.Module module : chassis.modules()) {
            List<HudElement> content = new ArrayList<>();
            if (!module.items().isEmpty()) {
                content.add(grid(module.items(), 3, 12));
            }
            module.names().stream().limit(MAX_LISTED).forEach(name -> content.add(label("plain", name)));
            if (content.isEmpty()) {
                content.add(label("nothing"));
            }
            modules.add(new HudElement.Module(module.icon(), content));
        }
        return new HudPanel(HudCategory.CHASSIS, 100, 100, List.of(new Modules(modules, takes(chassis.takes(), chassis.defaultRoute()))));
    }

    private static List<HudElement> takes(List<ItemBatch> takes, boolean defaultRoute) {
        List<HudElement> elements = new ArrayList<>();
        if (!takes.isEmpty()) {
            elements.add(grid(takes, 3, 12));
        }
        if (defaultRoute) {
            elements.add(label("default_route"));
        }
        return elements;
    }

    private static HudPanel basic(HudSource.Basic basic) {
        if (basic.takes().isEmpty()) {
            return basic.defaultRoute() ? new HudPanel(HudCategory.CHASSIS, 100, 35, List.of(label("default_route"))) : null;
        }
        List<HudElement> elements = new ArrayList<>();
        elements.add(label("takes"));
        elements.addAll(takes(basic.takes(), basic.defaultRoute()));
        return new HudPanel(HudCategory.CHASSIS, 100, 100, elements);
    }

    private static HudPanel provider(HudSource.Provider provider) {
        if (provider.provided().isEmpty()) {
            return null;
        }
        return new HudPanel(HudCategory.PROVIDER, 100, 110, List.of(label("provided"), grid(provider.provided(), 4, 12)));
    }

    // bigger while there is something left to craft
    private static HudPanel crafting(HudSource.Crafting crafting) {
        if (crafting.result().isEmpty() && crafting.orders().isEmpty()) {
            return null;
        }
        List<HudElement> elements = new ArrayList<>();
        crafting.result().ifPresent(result -> elements.add(new Entry(HudText.of(KEY + "result"), result)));
        if (crafting.orders().isEmpty()) {
            return new HudPanel(HudCategory.CRAFTING, 60, 47, elements);
        }
        elements.add(new Entry(HudText.of(KEY + "todo"), crafting.orders().get(0)));
        return new HudPanel(HudCategory.CRAFTING, 100, 58, elements);
    }

    private static HudPanel satellite(HudSource.Satellite satellite) {
        Label name = new Label(HudText.of(KEY + "satellite", satellite.name()), true);
        if (satellite.waiting().isEmpty()) {
            return new HudPanel(HudCategory.SATELLITE, 100, 35, List.of(name));
        }
        return new HudPanel(HudCategory.SATELLITE, 100, 100, List.of(name, grid(satellite.waiting(), 4, 12)));
    }

    private static HudPanel supplier(HudSource.Supplier supplier) {
        if (supplier.targets().isEmpty()) {
            return null;
        }
        return new HudPanel(HudCategory.CHASSIS, 100, 100, List.of(label("stocks"), grid(supplier.targets(), 3, 12)));
    }

    private static HudPanel connector(HudSource.Connector connector) {
        if (connector.expected().isEmpty() && connector.ticksEmpty() >= CONNECTOR_LINGER) {
            return null;
        }
        return new HudPanel(HudCategory.CONNECTOR, 100, 100, List.of(label("expected"), grid(connector.expected(), 3, 9)));
    }

    private static HudPanel power(HudSource.Power power) {
        return new HudPanel(HudCategory.POWER, 120, 80, List.of(label("stored_energy"), new HudElement.Bar(power.stored(), power.capacity()),
                label("energy", power.stored()), label("energy_max", power.capacity())));
    }
}
