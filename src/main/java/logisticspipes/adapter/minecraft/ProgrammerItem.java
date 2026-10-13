package logisticspipes.adapter.minecraft;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import logisticspipes.domain.program.Program;
import logisticspipes.domain.program.ProgramCatalog;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// flashed in a program compiler for one thing, then it is what the recipe of that thing asks for
public class ProgrammerItem extends Item {

    private static final String NAMESPACE = "routedpipes";

    // the id of the item the programmer is flashed for, the loader has to register this as a data component
    public static final DataComponentType<ResourceLocation> PROGRAM = DataComponentType.<ResourceLocation>builder()
            .persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC).build();

    public ProgrammerItem(Properties properties) {
        super(properties);
    }

    public static Item itemOf(Program program) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NAMESPACE, program.id()));
    }

    public static void flash(ItemStack programmer, Program program) {
        programmer.set(PROGRAM, ResourceLocation.fromNamespaceAndPath(NAMESPACE, program.id()));
    }

    // empty for a programmer nothing was flashed on, and for one with a program that is gone
    public static Optional<Program> programOf(ItemStack programmer) {
        ResourceLocation target = programmer.get(PROGRAM);
        if (!(programmer.getItem() instanceof ProgrammerItem) || target == null || !target.getNamespace().equals(NAMESPACE)) {
            return Optional.empty();
        }
        return ProgramCatalog.byId(target.getPath());
    }

    public static boolean flashedFor(ItemStack programmer, Item item) {
        return programOf(programmer).filter(program -> itemOf(program) == item).isPresent();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Optional<Program> program = programOf(stack);
        if (program.isEmpty()) {
            for (int line = 1; line <= 3; line++) {
                tooltip.add(Component.translatable(getDescriptionId() + ".empty." + line).withStyle(ChatFormatting.RED));
            }
            return;
        }
        tooltip.add(Component.translatable(getDescriptionId() + ".loaded." + program.get().kind().name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(itemOf(program.get()).getDescription().copy().withStyle(ChatFormatting.GRAY));
    }
}
