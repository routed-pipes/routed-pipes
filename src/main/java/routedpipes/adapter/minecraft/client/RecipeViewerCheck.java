package routedpipes.adapter.minecraft.client;

import java.util.Optional;
import java.util.function.Function;

import net.minecraft.client.gui.screens.Screen;

// test hook for the scripted client: a recipe viewer plugin offers a way to do to the open screen
// what a player does with the viewer, so the whole path down to the server can be checked without a mouse
public final class RecipeViewerCheck {

    // the plugin only offers a driver when the scripted client is running
    public static final String ENABLED_PROPERTY = "routedpipes.visualTest";

    private static Function<Screen, String> driver;

    private RecipeViewerCheck() {
    }

    // the driver returns an empty text when everything went through, else what went wrong
    public static void setDriver(Function<Screen, String> newDriver) {
        driver = newDriver;
    }

    // empty when no recipe viewer is installed
    public static Optional<String> run(Screen screen) {
        return driver == null ? Optional.empty() : Optional.of(driver.apply(screen));
    }
}
