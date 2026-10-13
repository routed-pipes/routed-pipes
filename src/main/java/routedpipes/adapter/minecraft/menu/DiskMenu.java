package routedpipes.adapter.minecraft.menu;

import java.util.function.Predicate;

import routedpipes.adapter.minecraft.DiskDrive;
import routedpipes.adapter.minecraft.DiskItem;
import routedpipes.domain.macro.Macro;
import routedpipes.domain.macro.MacroDisk;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

// the macros on the disk in a pipe: name the disk, order a macro, delete it or open it in the editor
public class DiskMenu extends AbstractContainerMenu implements TextMenu {

    public static final int TEXT_DISK_NAME = 0;
    // these carry the name of the macro they are about
    public static final int TEXT_REQUEST = 1;
    // no name starts a new macro
    public static final int TEXT_EDIT = 2;
    public static final int TEXT_DELETE = 3;
    public static final int BUTTON_BACK = 0;
    public static final int MAX_NAME = 32;

    private final DiskDrive drive;
    private final Predicate<Player> usable;
    private final Player viewer;

    // client side
    public DiskMenu(MenuType<?> type, int id) {
        this(type, id, null, player -> true, null);
    }

    // the disk is shown in a slot so that what is on it reaches the client
    public DiskMenu(MenuType<?> type, int id, DiskDrive drive, Predicate<Player> usable, Player viewer) {
        super(type, id);
        this.drive = drive;
        this.usable = usable;
        this.viewer = viewer;
        addSlot(new MarkerSlot(drive == null ? new SimpleContainer(1) : drive.slot(), 0, 152, 16));
    }

    public MacroDisk disk() {
        return DiskItem.read(slots.get(0).getItem());
    }

    static String clipped(String name) {
        String stripped = name.strip();
        return stripped.length() > MAX_NAME ? stripped.substring(0, MAX_NAME).strip() : stripped;
    }

    @Override
    public void setText(int field, String text) {
        if (drive == null) {
            return;
        }
        MacroDisk disk = drive.disk();
        switch (field) {
            case TEXT_DISK_NAME -> drive.write(disk.named(clipped(text)));
            case TEXT_REQUEST -> disk.macro(text).ifPresent(macro -> drive.request(viewer, macro));
            case TEXT_EDIT -> drive.openEditor(viewer, disk.macro(text).orElse(Macro.EMPTY));
            case TEXT_DELETE -> drive.write(disk.without(text));
            default -> { }
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (drive == null || id != BUTTON_BACK) {
            return false;
        }
        drive.close(player);
        return true;
    }

    // the disk only shows what is on it
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return usable.test(player);
    }
}
