package routedpipes.adapter.minecraft.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

// everything the mod puts into game registries, in the order it was declared
public final class Registrations {

    private final String namespace;
    private final List<Registered<?>> entries = new ArrayList<>();

    Registrations(String namespace) {
        this.namespace = namespace;
    }

    <R, T extends R> Registered<T> register(ResourceKey<? extends Registry<R>> registry, String name, Supplier<T> factory) {
        Registered<T> entry = new Registered<>(registry, ResourceLocation.fromNamespaceAndPath(namespace, name), factory);
        entries.add(entry);
        return entry;
    }

    public List<Registered<?>> entries() {
        return Collections.unmodifiableList(entries);
    }

    @SuppressWarnings("unchecked")
    public <R> List<Registered<? extends R>> entries(ResourceKey<? extends Registry<R>> registry) {
        List<Registered<? extends R>> matching = new ArrayList<>();
        for (Registered<?> entry : entries) {
            if (entry.registry().equals(registry)) {
                matching.add((Registered<? extends R>) entry);
            }
        }
        return matching;
    }
}
