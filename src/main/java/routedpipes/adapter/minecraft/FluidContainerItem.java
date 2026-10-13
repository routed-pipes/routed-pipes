package routedpipes.adapter.minecraft;

import java.util.List;
import java.util.Optional;

import routedpipes.domain.fluid.FluidBatch;
import routedpipes.domain.fluid.FluidType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// fluid packed for its way through the pipes, also stands for a fluid in filters and lists
// players never get to hold one
public class FluidContainerItem extends Item {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("routedpipes", "fluid_container");

    private final FluidStorage fluids;

    public FluidContainerItem(Properties properties, FluidStorage fluids) {
        super(properties.stacksTo(1));
        this.fluids = fluids;
    }

    public static ItemStack of(FluidBatch batch) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ID));
        stack.set(FluidContents.TYPE, FluidContents.of(batch));
        return stack;
    }

    // a container that only says which fluid is meant
    public static ItemStack marker(FluidType type) {
        return of(new FluidBatch(type, 1));
    }

    // the marker of the fluid in a clicked item like a bucket, empty if it holds none
    public static ItemStack markerOf(ItemStack clicked) {
        if (!(BuiltInRegistries.ITEM.get(ID) instanceof FluidContainerItem container)) {
            return ItemStack.EMPTY;
        }
        return fluidIn(clicked).or(() -> container.fluids.contained(clicked)).map(batch -> marker(batch.type())).orElse(ItemStack.EMPTY);
    }

    public static Optional<FluidBatch> fluidIn(ItemStack stack) {
        return Optional.ofNullable(stack.get(FluidContents.TYPE)).filter(contents -> contents.amount() > 0)
                .map(FluidContents::batch);
    }

    @Override
    public Component getName(ItemStack stack) {
        return fluidIn(stack).map(batch -> fluids.name(batch.type())).orElseGet(() -> super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        fluidIn(stack).filter(batch -> batch.amount() > 1).ifPresent(batch -> tooltip.add(
                Component.translatable("routedpipes.fluid.amount", batch.amount()).withStyle(ChatFormatting.GRAY)));
    }
}
