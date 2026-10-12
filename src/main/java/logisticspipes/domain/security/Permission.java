package logisticspipes.domain.security;

// what a security station can allow a player to do with the pipes locked to it
public enum Permission {
    // open the settings of a pipe or of the modules in it
    CONFIGURE_SETTINGS,
    // order items at a request pipe, also from afar
    ACTIVE_REQUESTING,
    // put upgrades into a pipe or take them out
    UPGRADE_PIPES,
    // look at a pipe with the pipe controller
    CHECK_NETWORK,
    // break a pipe
    REMOVE_PIPES,
    // use the channels this station secures, has nothing to do with locked pipes
    ACCESS_ROUTING_CHANNELS
}
