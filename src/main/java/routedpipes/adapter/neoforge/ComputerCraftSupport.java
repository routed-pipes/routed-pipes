package routedpipes.adapter.neoforge;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import routedpipes.adapter.computercraft.PipePeripheral;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

// makes routed pipes peripherals for the computers of CC: Tweaked, which is optional
final class ComputerCraftSupport {

    static final String MOD = "computercraft";

    private ComputerCraftSupport() {
    }

    static boolean installed() {
        return ModList.get().isLoaded(MOD);
    }

    static void registerIfInstalled(IEventBus modEventBus) {
        if (installed()) {
            modEventBus.addListener(Peripherals::register);
        }
    }

    // everything that needs classes of the mod is in here, the outer class has to load without them
    private static final class Peripherals {

        private static void register(RegisterCapabilitiesEvent event) {
            event.registerBlockEntity(PeripheralCapability.get(), RoutedPipes.PIPE_BLOCK_ENTITY.get(),
                    (pipe, side) -> PipePeripheral.of(pipe));
        }
    }
}
