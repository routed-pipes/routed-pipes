package logisticspipes.adapter.minecraft.menu;

import java.util.function.Predicate;

import logisticspipes.adapter.minecraft.DiskDrive;
import logisticspipes.adapter.minecraft.DiskItem;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.macro.Macro;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

// the request screen put to use for macros: what is picked there goes into the macro instead of being ordered
public class MacroEditMenu extends RequestMenu {

    public static final int BUTTON_SAVE = 20;
    public static final int BUTTON_BACK = 21;
    // plus the place of the entry in the macro
    public static final int BUTTON_REMOVE = 100;
    public static final int TEXT_NAME = 1;
    // hidden, carries the macro as it is so far to the client
    public static final int DRAFT_SLOT = OWN_SLOTS;

    private final Container draft = new SimpleContainer(1);
    private final DiskDrive drive;
    private Macro macro;

    // client side
    public MacroEditMenu(MenuType<?> type, int id) {
        this(type, id, null, player -> true, null, Macro.EMPTY);
    }

    public MacroEditMenu(MenuType<?> type, int id, PipeBlockEntity pipe, Predicate<Player> usable, DiskDrive drive,
            Macro macro) {
        super(type, id, pipe, usable);
        this.drive = drive;
        this.macro = macro;
        addSlot(new MarkerSlot(draft, 0, 0, 0, () -> false));
        if (drive != null) {
            showDraft();
        }
    }

    public Macro macro() {
        return DiskItem.readDraft(slots.get(DRAFT_SLOT).getItem());
    }

    private void showDraft() {
        ItemStack stack = new ItemStack(drive.slot().getItem(0).getItem());
        DiskItem.writeDraft(stack, macro);
        draft.setItem(0, stack);
    }

    @Override
    public boolean previews() {
        return false;
    }

    // the picked item and amount are added to the macro
    @Override
    protected void request(Player player) {
        if (selected() != null) {
            macro = macro.with(new ItemBatch(selected(), amount()));
            showDraft();
        }
    }

    @Override
    public void setText(int field, String text) {
        if (drive != null && field == TEXT_NAME) {
            macro = macro.named(DiskMenu.clipped(text));
            showDraft();
        } else {
            super.setText(field, text);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (drive == null) {
            return false;
        }
        if (id == BUTTON_SAVE) {
            // the screen says what is missing, nothing to do here until it is there
            if (macro.problem().isEmpty()) {
                drive.write(drive.disk().saved(macro));
                drive.open(player);
            }
        } else if (id == BUTTON_BACK) {
            drive.open(player);
        } else if (id >= BUTTON_REMOVE && id - BUTTON_REMOVE < macro.entries().size()) {
            macro = macro.without(macro.entries().get(id - BUTTON_REMOVE).type());
            showDraft();
        } else {
            return super.clickMenuButton(player, id);
        }
        return true;
    }
}
