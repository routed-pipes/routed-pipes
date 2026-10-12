package logisticspipes.domain.hud;

import java.util.Optional;

import logisticspipes.domain.logistics.PipeKind;

// the kinds of panels the glasses can be told to show or hide
public enum HudCategory {
    CHASSIS,
    CRAFTING,
    CONNECTOR,
    POWER,
    PROVIDER,
    SATELLITE;

    // empty for the pipes that never have a panel
    // basic and supplier pipes are a chassis with one fixed module here, so they go with the chassis
    public static Optional<HudCategory> of(PipeKind kind) {
        return Optional.ofNullable(switch (kind) {
            case CHASSIS, BASIC, SUPPLIER -> CHASSIS;
            case INVENTORY_SYSTEM_CONNECTOR -> CONNECTOR;
            case CRAFTING -> CRAFTING;
            case PROVIDER -> PROVIDER;
            case SATELLITE, FLUID_SATELLITE -> SATELLITE;
            default -> null;
        });
    }
}
