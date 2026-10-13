package logisticspipes.adapter.minecraft;

import java.util.function.Predicate;

import logisticspipes.adapter.minecraft.menu.DiskMenu;
import logisticspipes.adapter.minecraft.menu.MacroEditMenu;
import logisticspipes.application.RequestOutcome;
import logisticspipes.domain.macro.Macro;
import logisticspipes.domain.macro.MacroDisk;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// the place for a disk in a pipe and what is done with the macros on that disk
public final class DiskDrive {

    private final SimpleContainer slot = new SimpleContainer(1);
    private final PipeBlockEntity pipe;

    DiskDrive(PipeBlockEntity pipe) {
        this.pipe = pipe;
        slot.addListener(container -> pipe.setChanged());
    }

    public Container slot() {
        return slot;
    }

    public boolean holdsDisk() {
        return slot.getItem(0).getItem() instanceof DiskItem;
    }

    // takes the held disk if there is room for it
    boolean insert(ItemStack held) {
        if (!slot.isEmpty() || !(held.getItem() instanceof DiskItem)) {
            return false;
        }
        slot.setItem(0, held.split(1));
        return true;
    }

    // the disk comes out lying on the pipe
    public void eject() {
        pipe.popOut(slot.removeItemNoUpdate(0));
        slot.setChanged();
    }

    public MacroDisk disk() {
        return DiskItem.read(slot.getItem(0));
    }

    public void write(MacroDisk disk) {
        if (!holdsDisk()) {
            return;
        }
        ItemStack written = slot.getItem(0).copy();
        DiskItem.write(written, disk);
        slot.setItem(0, written);
    }

    // orders everything in the macro to the pipe, all of it or nothing
    public RequestOutcome request(Player player, Macro macro) {
        RequestOutcome outcome = pipe.requestList(macro.entries());
        if (outcome.started()) {
            player.displayClientMessage(Component.translatable("logisticspipes.request.macro_started", macro.name()), false);
        } else {
            PipeMenus.tellWhatIsMissing(player, outcome);
        }
        return outcome;
    }

    private Predicate<Player> usable() {
        return user -> Container.stillValidBlockEntity(pipe, user) && holdsDisk();
    }

    // the list of macros on the disk
    public void open(Player player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, user) -> new DiskMenu(
                pipe.services().diskMenus().disk().get(), id, this, usable(), user),
                Component.translatable("logisticspipes.gui.disk")));
    }

    // the request screen of the pipe, set up to put together a macro
    public void openEditor(Player player, Macro macro) {
        player.openMenu(new SimpleMenuProvider((id, inventory, user) -> new MacroEditMenu(
                pipe.services().diskMenus().editor().get(), id, pipe, usable(), this, macro),
                Component.translatable("logisticspipes.gui.macro")));
    }

    // back to the screen of the pipe itself
    public void close(Player player) {
        player.openMenu(pipe.menuProvider());
    }
}
