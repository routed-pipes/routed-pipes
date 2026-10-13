package routedpipes.domain.power;

// a machine of another mod that takes energy
public interface EnergyReceiver {

    // how much it is short of being full
    int missing();

    // returns how much it took
    int receive(int amount);
}
