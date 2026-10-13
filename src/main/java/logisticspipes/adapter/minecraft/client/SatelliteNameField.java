package logisticspipes.adapter.minecraft.client;

import logisticspipes.adapter.minecraft.menu.SatelliteNames;
import logisticspipes.domain.crafting.SatelliteName;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

// where the name of a satellite pipe is typed in, the button under it or the enter key sends it to the server
final class SatelliteNameField {

    private static final int WIDTH = 120;

    private final EditBox box;
    private final Button save;
    private final int containerId;
    // the name the server last told, the box follows it until someone types
    private String told = "";

    // centre is the middle of the box
    SatelliteNameField(Font font, int centre, int top, int containerId) {
        this.containerId = containerId;
        box = new EditBox(font, centre - WIDTH / 2, top, WIDTH, 14, Component.translatable("routedpipes.gui.satellite.name"));
        box.setMaxLength(SatelliteName.MAX_LENGTH);
        save = Button.builder(Component.translatable("routedpipes.gui.satellite.save"), button -> send())
                .bounds(centre - 24, top + 18, 48, 14).build();
    }

    EditBox box() {
        return box;
    }

    Button save() {
        return save;
    }

    private void send() {
        ClientMenuLink.setText(containerId, SatelliteNames.FIELD_OWN, box.getValue());
    }

    // called every tick with the name as the menu has it
    void show(String name) {
        if (!name.equals(told)) {
            told = name;
            box.setValue(name);
        }
    }

    // typing a name must not trigger hotkeys like closing the screen with the inventory key
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!box.isFocused() || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            send();
        } else {
            box.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }
}
