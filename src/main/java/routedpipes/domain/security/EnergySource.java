package routedpipes.domain.security;

// where the energy for a check comes from
@FunctionalInterface
public interface EnergySource {

    // takes all of the amount or nothing, false when there was not enough
    boolean use(int amount);
}
