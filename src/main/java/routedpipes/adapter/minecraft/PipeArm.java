package routedpipes.adapter.minecraft;

import net.minecraft.util.StringRepresentable;

// what a side of a pipe looks like: no arm, or one with the red or the green light of a routed pipe
// three values per side keep a pipe at 729 states, a second on/off property per side would make it 4096
public enum PipeArm implements StringRepresentable {
    NONE("none"),
    // connected, but no other routed pipe is reached this way
    UNROUTED("unrouted"),
    ROUTED("routed");

    private final String name;

    PipeArm(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
