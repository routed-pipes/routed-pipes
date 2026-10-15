package routedpipes.adapter.minecraft.gametest;

// the energy of a block as cables and machines of other mods see it
public interface OutsideEnergy {

    boolean canReceive();

    boolean canExtract();

    int receive(int amount, boolean simulate);

    int extract(int amount, boolean simulate);

    int stored();

    int capacity();
}
