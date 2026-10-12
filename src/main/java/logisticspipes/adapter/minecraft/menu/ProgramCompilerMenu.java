package logisticspipes.adapter.minecraft.menu;

import java.util.Optional;

import logisticspipes.adapter.minecraft.CompilerDiskContents;
import logisticspipes.adapter.minecraft.DiskItem;
import logisticspipes.adapter.minecraft.ProgramCompilerBlockEntity;
import logisticspipes.adapter.minecraft.ProgrammerItem;
import logisticspipes.domain.program.CompilerKnowledge;
import logisticspipes.domain.program.CompilerTask;
import logisticspipes.domain.program.Program;
import logisticspipes.domain.program.ProgramCatalog;
import logisticspipes.domain.program.ProgramCategory;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// the program compiler: a disk, a programmer and the task that is running
// what the disk knows comes with the disk itself, so the lists are put together on the client
public class ProgramCompilerMenu extends AbstractContainerMenu {

    public static final int DISK_SLOT = 0;
    public static final int PROGRAMMER_SLOT = 1;
    public static final int INVENTORY_TOP = 104;
    private static final int SLOTS = 38;
    // a button is one of these plus the number of the category or of the program in the catalog
    private static final int UNLOCK = 0;
    private static final int COMPILE = 100;
    private static final int FLASH = 200;
    private static final int PROGRESS_STEPS = 1000;

    private static final int DATA_TASK = 0;
    private static final int DATA_TARGET = 1;
    private static final int DATA_PROGRESS = 2;
    private static final int DATA_POWERED = 3;

    private final ContainerData data;
    private final ProgramCompilerBlockEntity compiler;

    // client side
    public ProgramCompilerMenu(MenuType<?> type, int id, Inventory inventory) {
        this(type, id, inventory, null, new SimpleContainer(2), new SimpleContainerData(4));
    }

    public ProgramCompilerMenu(MenuType<?> type, int id, Inventory inventory, ProgramCompilerBlockEntity compiler) {
        this(type, id, inventory, compiler, compiler.slots(), new ContainerData() {
            @Override
            public int get(int index) {
                Optional<CompilerTask> task = compiler.compiler().task();
                return switch (index) {
                    case DATA_TASK -> task.map(running -> running.type().ordinal() + 1).orElse(0);
                    case DATA_TARGET -> task.map(ProgramCompilerMenu::target).orElse(0);
                    case DATA_PROGRESS -> (int) (compiler.compiler().progress() * PROGRESS_STEPS);
                    default -> compiler.compiler().powered() ? 1 : 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 4;
            }
        });
    }

    private ProgramCompilerMenu(MenuType<?> type, int id, Inventory inventory, ProgramCompilerBlockEntity compiler,
            Container slots, ContainerData data) {
        super(type, id);
        this.compiler = compiler;
        this.data = data;
        addSlot(new DiskSlot(slots, ProgramCompilerBlockEntity.DISK_SLOT, 8, 8));
        addSlot(new Slot(slots, ProgramCompilerBlockEntity.PROGRAMMER_SLOT, 152, 8) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof ProgrammerItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, INVENTORY_TOP + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_TOP + 58));
        }
        addDataSlots(data);
    }

    public static int unlockButton(ProgramCategory category) {
        return UNLOCK + category.ordinal();
    }

    public static int compileButton(Program program) {
        return COMPILE + ProgramCatalog.all().indexOf(program);
    }

    public static int flashButton(Program program) {
        return FLASH + ProgramCatalog.all().indexOf(program);
    }

    private static int target(CompilerTask task) {
        return task.program() == null ? task.category().ordinal() : ProgramCatalog.all().indexOf(task.program());
    }

    // the task a button stands for, a client could send any number
    private static Optional<CompilerTask> task(int button) {
        if (button >= UNLOCK && button < UNLOCK + ProgramCategory.values().length) {
            return Optional.of(CompilerTask.unlock(ProgramCategory.values()[button - UNLOCK]));
        }
        int programs = ProgramCatalog.all().size();
        if (button >= COMPILE && button < COMPILE + programs) {
            return Optional.of(CompilerTask.compile(ProgramCatalog.all().get(button - COMPILE)));
        }
        if (button >= FLASH && button < FLASH + programs) {
            return Optional.of(CompilerTask.flash(ProgramCatalog.all().get(button - FLASH)));
        }
        return Optional.empty();
    }

    // the task that is running
    public Optional<CompilerTask> task() {
        int type = data.get(DATA_TASK);
        if (type <= 0 || type > CompilerTask.Type.values().length) {
            return Optional.empty();
        }
        return task(new int[] {UNLOCK, COMPILE, FLASH}[type - 1] + data.get(DATA_TARGET));
    }

    // from 0 to 1
    public float progress() {
        return (float) data.get(DATA_PROGRESS) / PROGRESS_STEPS;
    }

    // false while no basic logistics pipe next to the compiler has the energy for the running task
    public boolean powered() {
        return data.get(DATA_POWERED) != 0;
    }

    // what the disk in the slot knows, empty without a disk
    public Optional<CompilerKnowledge> disk() {
        return CompilerDiskContents.read(getSlot(DISK_SLOT).getItem());
    }

    public boolean holdsProgrammer() {
        return getSlot(PROGRAMMER_SLOT).hasItem();
    }

    // the compiler itself looks at the disk again, a button for something the disk does not allow does nothing
    @Override
    public boolean clickMenuButton(Player player, int button) {
        Optional<CompilerTask> task = task(button);
        if (task.isEmpty()) {
            return false;
        }
        return compiler == null || compiler.start(task.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index <= PROGRAMMER_SLOT) {
            moved = moveItemStackTo(stack, PROGRAMMER_SLOT + 1, SLOTS, true);
        } else if (stack.getItem() instanceof DiskItem) {
            moved = moveItemStackTo(stack, DISK_SLOT, DISK_SLOT + 1, false);
        } else {
            moved = stack.getItem() instanceof ProgrammerItem && moveItemStackTo(stack, PROGRAMMER_SLOT, PROGRAMMER_SLOT + 1, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return compiler == null || Container.stillValidBlockEntity(compiler, player);
    }
}
