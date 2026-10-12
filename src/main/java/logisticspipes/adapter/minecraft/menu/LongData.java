package logisticspipes.adapter.minecraft.menu;

import java.util.function.LongSupplier;

import net.minecraft.world.inventory.ContainerData;

// a data slot reaches the client as 16 bits, so every number in here is spread over four of them
final class LongData implements ContainerData {

    private static final int PARTS = 4;
    private static final int BITS = 16;
    private static final int MASK = 0xFFFF;

    // null on the client, which only has what it was sent
    private final LongSupplier[] sources;
    private final int[] received;

    // server side
    LongData(LongSupplier... sources) {
        this.sources = sources;
        this.received = new int[sources.length * PARTS];
    }

    // client side
    LongData(int numbers) {
        this.sources = null;
        this.received = new int[numbers * PARTS];
    }

    long value(int number) {
        long value = 0;
        for (int part = 0; part < PARTS; part++) {
            value |= (long) get(number * PARTS + part) << BITS * part;
        }
        return value;
    }

    @Override
    public int get(int index) {
        if (sources == null) {
            return received[index];
        }
        return (int) (sources[index / PARTS].getAsLong() >>> BITS * (index % PARTS)) & MASK;
    }

    // what arrives was a short on the way and comes back with its sign
    @Override
    public void set(int index, int value) {
        received[index] = value & MASK;
    }

    @Override
    public int getCount() {
        return received.length;
    }
}
