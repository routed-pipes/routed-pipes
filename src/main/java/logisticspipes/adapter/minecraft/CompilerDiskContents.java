package logisticspipes.adapter.minecraft;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import logisticspipes.domain.program.CompilerKnowledge;
import logisticspipes.domain.program.Program;
import logisticspipes.domain.program.ProgramCatalog;
import logisticspipes.domain.program.ProgramCategory;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

// what a program compiler has written on a disk: the unlocked categories and the compiled programs by name
public record CompilerDiskContents(List<String> categories, List<String> programs) {

    public static final Codec<CompilerDiskContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("categories", List.of()).forGetter(CompilerDiskContents::categories),
            Codec.STRING.listOf().optionalFieldOf("programs", List.of()).forGetter(CompilerDiskContents::programs))
            .apply(instance, CompilerDiskContents::new));

    public static final StreamCodec<ByteBuf, CompilerDiskContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), CompilerDiskContents::categories,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), CompilerDiskContents::programs,
            CompilerDiskContents::new);

    // the loader has to register this as a data component
    public static final DataComponentType<CompilerDiskContents> TYPE = DataComponentType.<CompilerDiskContents>builder()
            .persistent(CODEC).networkSynchronized(STREAM_CODEC).build();

    // empty for anything that is not a disk, a disk fresh from the crafting table knows nothing
    public static Optional<CompilerKnowledge> read(ItemStack stack) {
        if (!(stack.getItem() instanceof DiskItem)) {
            return Optional.empty();
        }
        CompilerDiskContents contents = stack.get(TYPE);
        return Optional.of(contents == null ? CompilerKnowledge.EMPTY : contents.knowledge());
    }

    public static void write(ItemStack disk, CompilerKnowledge knowledge) {
        disk.set(TYPE, new CompilerDiskContents(
                knowledge.categories().stream().map(category -> category.name().toLowerCase(Locale.ROOT)).sorted().toList(),
                knowledge.programs().stream().map(Program::id).sorted().toList()));
    }

    // names that mean nothing any more are left out
    private CompilerKnowledge knowledge() {
        Set<ProgramCategory> unlocked = new LinkedHashSet<>();
        Arrays.stream(ProgramCategory.values())
                .filter(category -> categories.contains(category.name().toLowerCase(Locale.ROOT))).forEach(unlocked::add);
        Set<Program> compiled = new LinkedHashSet<>();
        programs.forEach(id -> ProgramCatalog.byId(id).ifPresent(compiled::add));
        return new CompilerKnowledge(unlocked, compiled);
    }
}
