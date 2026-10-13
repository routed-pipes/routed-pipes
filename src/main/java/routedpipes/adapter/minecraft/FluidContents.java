package routedpipes.adapter.minecraft;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import routedpipes.domain.fluid.FluidBatch;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;

// the fluid in a fluid container item or in a tank of a pipe, amount in mB
public record FluidContents(Fluid fluid, DataComponentPatch components, int amount) {

    public static final Codec<FluidContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(FluidContents::fluid),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(FluidContents::components),
            Codec.INT.fieldOf("amount").forGetter(FluidContents::amount)).apply(instance, FluidContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FluidContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.FLUID), FluidContents::fluid,
            DataComponentPatch.STREAM_CODEC, FluidContents::components,
            ByteBufCodecs.VAR_INT, FluidContents::amount,
            FluidContents::new);

    // the loader has to register this as a data component
    public static final DataComponentType<FluidContents> TYPE = DataComponentType.<FluidContents>builder()
            .persistent(CODEC).networkSynchronized(STREAM_CODEC).build();

    public static FluidContents of(FluidBatch batch) {
        McFluidType type = (McFluidType) batch.type();
        return new FluidContents(type.fluid(), type.components(), batch.amount());
    }

    public McFluidType type() {
        return new McFluidType(fluid, components);
    }

    public FluidBatch batch() {
        return new FluidBatch(type(), amount);
    }
}
