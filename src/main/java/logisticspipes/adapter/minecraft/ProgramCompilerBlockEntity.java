package logisticspipes.adapter.minecraft;

import java.util.Arrays;
import java.util.Optional;

import logisticspipes.adapter.minecraft.menu.ProgramCompilerMenu;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.program.CompilerKnowledge;
import logisticspipes.domain.program.CompilerTask;
import logisticspipes.domain.program.ProgramCatalog;
import logisticspipes.domain.program.ProgramCategory;
import logisticspipes.domain.program.ProgramCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

// holds a disk and a programmer and works on them with the energy of the basic logistics pipes around it
public class ProgramCompilerBlockEntity extends BlockEntity {

    public static final int DISK_SLOT = 0;
    public static final int PROGRAMMER_SLOT = 1;

    private final SimpleContainer slots = new SimpleContainer(2);
    private final ProgramCompiler compiler = new ProgramCompiler();

    public ProgramCompilerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        slots.addListener(container -> setChanged());
    }

    public Container slots() {
        return slots;
    }

    public ProgramCompiler compiler() {
        return compiler;
    }

    private Optional<CompilerKnowledge> disk() {
        return CompilerDiskContents.read(slots.getItem(DISK_SLOT));
    }

    private boolean holdsProgrammer() {
        return slots.getItem(PROGRAMMER_SLOT).getItem() instanceof ProgrammerItem;
    }

    // false when another task is running or the disk in here does not allow this one
    public boolean start(CompilerTask task) {
        boolean started = compiler.start(task, disk(), holdsProgrammer());
        if (started) {
            setChanged();
        }
        return started;
    }

    void serverTick() {
        if (compiler.task().isEmpty()) {
            return;
        }
        compiler.tick(disk(), holdsProgrammer(), this::payingPipes).ifPresent(this::finish);
        setChanged();
    }

    // every basic logistics pipe around the compiler that has the energy pays, the side above does not count
    private int payingPipes() {
        int paying = 0;
        for (Direction side : Direction.values()) {
            BlockPos target = worldPosition.relative(side);
            if (side != Direction.UP && level.isLoaded(target) && level.getBlockEntity(target) instanceof PipeBlockEntity pipe
                    && pipe.kind() == PipeKind.BASIC && pipe.usePower(CompilerTask.ENERGY_PER_TICK)) {
                paying++;
            }
        }
        return paying;
    }

    private void finish(CompilerTask task) {
        if (task.type() == CompilerTask.Type.FLASH) {
            ItemStack programmer = slots.getItem(PROGRAMMER_SLOT).copy();
            ProgrammerItem.flash(programmer, task.program());
            slots.setItem(PROGRAMMER_SLOT, programmer);
        } else {
            ItemStack written = slots.getItem(DISK_SLOT).copy();
            CompilerDiskContents.write(written, task.writtenTo(disk().orElseThrow()));
            slots.setItem(DISK_SLOT, written);
        }
    }

    MenuProvider menuProvider() {
        ProgramCompilerBlock block = (ProgramCompilerBlock) getBlockState().getBlock();
        return new SimpleMenuProvider((id, inventory, player) -> new ProgramCompilerMenu(block.services().menu().get(), id,
                inventory, this), block.getName());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", ContainerTags.save(slots, registries));
        compiler.task().ifPresent(task -> {
            tag.putString("Task", task.type().name());
            tag.putString("Target", task.program() == null ? task.category().name() : task.program().id());
            tag.putInt("Work", compiler.work());
        });
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerTags.load(slots, tag.getCompound("Items"), registries);
        savedTask(tag.getString("Task"), tag.getString("Target")).ifPresent(task -> compiler.restore(task, tag.getInt("Work")));
    }

    // empty when nothing was running, or what was does not exist any more
    private static Optional<CompilerTask> savedTask(String type, String target) {
        return switch (type) {
            case "UNLOCK" -> Arrays.stream(ProgramCategory.values()).filter(category -> category.name().equals(target))
                    .findFirst().map(CompilerTask::unlock);
            case "COMPILE" -> ProgramCatalog.byId(target).map(CompilerTask::compile);
            case "FLASH" -> ProgramCatalog.byId(target).map(CompilerTask::flash);
            default -> Optional.empty();
        };
    }
}
