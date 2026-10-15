package routedpipes.adapter.minecraft.gametest;

import routedpipes.adapter.minecraft.content.Content;
import java.util.List;
import java.util.Set;

import routedpipes.adapter.minecraft.ModuleItem;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.menu.ChassisMenu;
import routedpipes.adapter.minecraft.menu.MenuRequests;
import routedpipes.adapter.minecraft.menu.PipeConfigMenu;
import routedpipes.adapter.minecraft.menu.SatelliteNames;
import routedpipes.domain.logistics.ModuleKind;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

// satellites go by a name a player types, crafters pick one of the names within reach
public class SatelliteGameTest {

    private static final String EMPTY = "empty";
    // ticks the pipes get to find each other
    private static final int SETTLE = 15;

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, Content.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    private static void type(TestPlayer player, int field, String text) {
        MenuRequests.applyText(player, player.containerMenu.containerId, field, text);
    }

    @GameTest(template = EMPTY)
    public static void aSatelliteGetsTheNameTypedIntoItsSettings(GameTestHelper helper) {
        PipeBlockEntity satellite = pipe(helper, PipeKind.SATELLITE, 0);
        TestPlayer player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            player.openMenu(satellite.menuProvider());
            PipeConfigMenu menu = (PipeConfigMenu) player.containerMenu;
            helper.assertTrue(menu.satelliteName().isEmpty(), "a new satellite has no name");

            type(player, SatelliteNames.FIELD_OWN, "  Furnace top ");
            helper.assertTrue(satellite.satelliteName().equals("Furnace top"), "the name without the blanks around it: " + satellite.satelliteName());
            helper.assertTrue(menu.satelliteName().equals("Furnace top"), "the menu carries the name to the client");

            type(player, SatelliteNames.FIELD_OWN, "   ");
            helper.assertTrue(satellite.satelliteName().equals("Furnace top"), "a blank name is refused");
            type(player, SatelliteNames.FIELD_SLOT, "Other");
            helper.assertTrue(satellite.crafts().isEmpty() && menu.slotSatellite(0).isEmpty(), "a satellite has no slots to set satellites for");

            PipeBlockEntity loaded = pipe(helper, PipeKind.SATELLITE, 4);
            loaded.loadWithComponents(satellite.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertTrue(loaded.satelliteName().equals("Furnace top"), "the name is saved");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void aNameAnotherSatelliteInReachHasIsRefused(GameTestHelper helper) {
        PipeBlockEntity first = pipe(helper, PipeKind.SATELLITE, 0);
        PipeBlockEntity second = pipe(helper, PipeKind.SATELLITE, 1);
        PipeBlockEntity fluid = pipe(helper, PipeKind.FLUID_SATELLITE, 2);
        first.setSatelliteName("Ore");
        TestPlayer player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            player.openMenu(second.menuProvider());
            type(player, SatelliteNames.FIELD_OWN, "Ore");
            helper.assertTrue(second.satelliteName().isEmpty(), "the name is taken");
            type(player, SatelliteNames.FIELD_OWN, "Coal");
            helper.assertTrue(second.satelliteName().equals("Coal"), "another name is fine");
            type(player, SatelliteNames.FIELD_OWN, "Coal");
            helper.assertTrue(second.satelliteName().equals("Coal"), "its own name is not in its way");

            player.openMenu(fluid.menuProvider());
            type(player, SatelliteNames.FIELD_OWN, "Ore");
            helper.assertTrue(fluid.satelliteName().equals("Ore"), "fluid satellites have names of their own");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void aCraftingPipeListsTheSatellitesInReachAndTakesThePickedOne(GameTestHelper helper) {
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 0);
        pipe(helper, PipeKind.SATELLITE, 1).setSatelliteName("Near");
        pipe(helper, PipeKind.SATELLITE, 2);
        pipe(helper, PipeKind.SATELLITE, 3).setSatelliteName("Far");
        pipe(helper, PipeKind.FLUID_SATELLITE, 4).setSatelliteName("Tank");
        crafting.config().setItem(PipeBlockEntity.SATELLITE_ROW, new ItemStack(Items.RAW_IRON));
        crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.IRON_INGOT));
        TestPlayer player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            player.openMenu(crafting.menuProvider());
            PipeConfigMenu menu = (PipeConfigMenu) player.containerMenu;
            helper.assertTrue(menu.satellitesInReach().equals(List.of("Near", "Far")),
                    "named item satellites, nearest first: " + menu.satellitesInReach());

            type(player, SatelliteNames.FIELD_OWN, "Far");
            helper.assertTrue(crafting.satelliteName().equals("Far") && menu.satelliteName().equals("Far"), "the picked satellite");
            helper.assertTrue(crafting.crafts().get(0).bySatellite().keySet().equals(Set.of("Far")), "the bottom row goes there");
            type(player, SatelliteNames.FIELD_SLOT, "Near");
            helper.assertTrue(menu.slotSatellite(0).isEmpty(), "no satellites for single slots without the upgrade");

            type(player, SatelliteNames.FIELD_OWN, "");
            helper.assertTrue(crafting.satelliteName().isEmpty() && crafting.crafts().get(0).bySatellite().isEmpty(), "unset again");
            helper.succeed();
        });
    }

    // worlds from before satellites had names saved a number, the pipes keep finding each other by it
    @GameTest(template = EMPTY)
    public static void theNumberOfAnOldWorldBecomesTheName(GameTestHelper helper) {
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 0);
        PipeBlockEntity satellite = pipe(helper, PipeKind.SATELLITE, 1);
        CompoundTag oldSatellite = new CompoundTag();
        oldSatellite.putInt("Satellite", 7);
        CompoundTag oldCrafting = new CompoundTag();
        oldCrafting.putInt("Satellite", 7);
        oldCrafting.putIntArray("SlotSatellites", new int[] {0, 3, 0, 0, 0, 0, 0, 0, 0});

        satellite.loadWithComponents(oldSatellite, helper.getLevel().registryAccess());
        crafting.loadWithComponents(oldCrafting, helper.getLevel().registryAccess());
        crafting.config().setItem(1, new ItemStack(Items.COAL));
        crafting.config().setItem(PipeBlockEntity.SATELLITE_ROW, new ItemStack(Items.RAW_IRON));
        crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.IRON_INGOT));

        helper.assertTrue(satellite.satelliteName().equals("7"), "satellite 7 is now called 7");
        helper.assertTrue(crafting.crafts().get(0).bySatellite().keySet().equals(Set.of("7")), "and the crafting pipe still points at it");
        crafting.installUpgrade(new ItemStack(Content.UPGRADES.get(routedpipes.domain.logistics.UpgradeKind.ADVANCED_SATELLITE).get()));
        helper.assertTrue(crafting.crafts().get(0).bySatellite().keySet().equals(Set.of("3")), "the numbers of single slots as well");

        ItemStack module = new ItemStack(Content.MODULES.get(ModuleKind.CRAFTER).get());
        ModuleItem.configureCrafter(module, List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, new ItemStack(Items.RAW_IRON)), new ItemStack(Items.IRON_INGOT), "", 0);
        CustomData.update(DataComponents.CUSTOM_DATA, module, tag -> tag.putInt("LogisticsPipesSatellite", 5));
        helper.assertTrue(ModuleItem.craftingOf(module, false).orElseThrow().bySatellite().keySet().equals(Set.of("5")),
                "an old crafter module too");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aCrafterModuleInAChassisPicksFromTheSatellitesInReach(GameTestHelper helper) {
        BlockPos chassisPos = new BlockPos(0, 1, 1);
        helper.setBlock(chassisPos, Content.CHASSIS_PIPES.get(1).get());
        PipeBlockEntity chassis = helper.getBlockEntity(chassisPos);
        pipe(helper, PipeKind.SATELLITE, 1).setSatelliteName("Ore");
        ItemStack module = new ItemStack(Content.MODULES.get(ModuleKind.CRAFTER).get());
        ModuleItem.configureCrafter(module, List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, new ItemStack(Items.RAW_IRON)), new ItemStack(Items.IRON_INGOT), "", 0);
        chassis.modules().setItem(0, module);
        TestPlayer player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            player.openMenu(chassis.menuProvider());
            player.containerMenu.clickMenuButton(player, ChassisMenu.BUTTON_SETTINGS);
            PipeConfigMenu menu = (PipeConfigMenu) player.containerMenu;
            helper.assertTrue(menu.satellitesInReach().equals(List.of("Ore")), "the satellites the chassis reaches: " + menu.satellitesInReach());

            type(player, SatelliteNames.FIELD_OWN, "Ore");
            helper.assertTrue(menu.satelliteName().equals("Ore"), "the module keeps the pick");
            helper.assertTrue(chassis.crafts().get(0).bySatellite().keySet().equals(Set.of("Ore")), "and its bottom row goes there");

            // in the hand there is no network to look for satellites in
            player.getInventory().setItem(0, module);
            module.getItem().use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
            PipeConfigMenu inHand = (PipeConfigMenu) player.containerMenu;
            helper.assertTrue(inHand.satellitesInReach().isEmpty() && inHand.satelliteName().equals("Ore"),
                    "in the hand the pick shows but there is nothing to pick from");
            helper.succeed();
        });
    }
}
