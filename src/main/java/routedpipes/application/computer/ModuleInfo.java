package routedpipes.application.computer;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.logistics.ModuleKind;
import routedpipes.domain.logistics.PipeKind;

// a module as a script gets to see it, also for what is built into a pipe
// the type is the name the original mod gave it, filter and default route are empty for modules without them
public record ModuleInfo(String type, String kind, boolean hasGui, Optional<List<ItemBatch>> filter, Optional<Boolean> defaultRoute) {

    public static final String PLAIN = "LogisticsModule";

    public ModuleInfo {
        filter = filter.map(List::copyOf);
    }

    // filter and default route are only kept for the modules that have them
    public static ModuleInfo of(ModuleKind kind, boolean hasGui, List<ItemBatch> filter, boolean defaultRoute) {
        return new ModuleInfo(typeOf(kind), kind.name().toLowerCase(Locale.ROOT), hasGui,
                hasFilter(kind) ? Optional.of(filter) : Optional.empty(),
                kind == ModuleKind.ITEM_SINK ? Optional.of(defaultRoute) : Optional.empty());
    }

    public static String typeOf(ModuleKind kind) {
        return switch (kind) {
            case ITEM_SINK -> "ItemSink Module";
            case PROVIDER -> "Provider Module";
            case TERMINUS -> "Terminus Module";
            case ENCHANTMENT_SINK_MK2 -> "EnchantmentSink Module MK2";
            default -> PLAIN;
        };
    }

    // the modules whose list of items a script can read
    public static boolean hasFilter(ModuleKind kind) {
        return !typeOf(kind).equals(PLAIN) || kind == ModuleKind.ADVANCED_EXTRACTOR;
    }

    // the module a pipe of this kind works like, empty if there is none
    public static Optional<ModuleKind> builtInto(PipeKind pipe) {
        return switch (pipe) {
            case BASIC -> Optional.of(ModuleKind.ITEM_SINK);
            case PROVIDER -> Optional.of(ModuleKind.PROVIDER);
            default -> Optional.empty();
        };
    }
}
