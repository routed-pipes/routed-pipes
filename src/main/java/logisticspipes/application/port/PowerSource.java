package logisticspipes.application.port;

// one store of energy, like a power junction, that several routers can draw from
public interface PowerSource {

    // the same store has the same key whichever router reaches it
    Object key();

    // returns how much it gave, with simulate nothing is taken
    int draw(int amount, boolean simulate);
}
