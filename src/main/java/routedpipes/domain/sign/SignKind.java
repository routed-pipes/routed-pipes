package routedpipes.domain.sign;

import routedpipes.domain.logistics.PipeKind;

// what a sign on the face of a routed pipe shows
public enum SignKind {
    // what the crafting pipe it hangs on makes
    CRAFTING,
    // how many of one item the network holds
    ITEM_AMOUNT;

    // pipe is null for plain pipes, a request table is a full block with no room for a sign
    public boolean allowedOn(PipeKind pipe) {
        if (pipe == null || pipe == PipeKind.REQUEST_TABLE) {
            return false;
        }
        return this == ITEM_AMOUNT || pipe == PipeKind.CRAFTING;
    }

    // the sign creator steps through the kinds
    public SignKind next() {
        SignKind[] kinds = values();
        return kinds[(ordinal() + 1) % kinds.length];
    }
}
