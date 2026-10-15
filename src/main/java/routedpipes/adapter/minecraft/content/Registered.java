package routedpipes.adapter.minecraft.content;

import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

// something of the mod in a game registry, the loader registers it and then hands it out through here
public final class Registered<T> implements Supplier<T> {

    private final ResourceKey<? extends Registry<?>> registry;
    private final ResourceLocation id;
    private final Supplier<? extends T> factory;
    private Supplier<? extends T> bound;

    Registered(ResourceKey<? extends Registry<?>> registry, ResourceLocation id, Supplier<? extends T> factory) {
        this.registry = registry;
        this.id = id;
        this.factory = factory;
    }

    public ResourceKey<? extends Registry<?>> registry() {
        return registry;
    }

    public ResourceLocation id() {
        return id;
    }

    // only the loader calls these two
    public Supplier<? extends T> factory() {
        return factory;
    }

    public void bind(Supplier<? extends T> registered) {
        bound = registered;
    }

    @Override
    public T get() {
        if (bound == null) {
            throw new IllegalStateException(id + " is not registered yet");
        }
        return bound.get();
    }
}
