package routedpipes.domain.logistics;

import java.util.ArrayList;
import java.util.List;

// which module slots of a chassis take settings copied from another chassis
public final class ModuleMatch {

    private ModuleMatch() {
    }

    // slots that hold the same kind of module in both, empty slots are null, the chassis may differ in size
    public static List<Integer> slots(List<ModuleKind> copied, List<ModuleKind> present) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < Math.min(copied.size(), present.size()); slot++) {
            if (copied.get(slot) != null && copied.get(slot) == present.get(slot)) {
                slots.add(slot);
            }
        }
        return slots;
    }
}
