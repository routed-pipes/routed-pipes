package routedpipes.adapter.minecraft.gametest;

import routedpipes.adapter.minecraft.content.Content;
import java.util.List;

import routedpipes.adapter.minecraft.McFluidType;
import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.ModuleItem;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.menu.ChassisMenu;
import routedpipes.adapter.minecraft.menu.MenuRequests;
import routedpipes.adapter.minecraft.menu.PipeConfigMenu;
import routedpipes.adapter.minecraft.menu.SatelliteNames;
import routedpipes.application.RequestOutcome;
import routedpipes.domain.crafting.FluidInput;
import routedpipes.domain.logistics.ModuleKind;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.UpgradeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

// crafters that take fluid: set up in the settings, delivered to a fluid satellite together with the items of the request
public class FluidCraftingGameTest {

    private static final String EMPTY = "empty";
    // long enough for every pipe to find its neighbours
    private static final int SETTLE = 15;
    private static final int FIRST_FLUID = PipeBlockEntity.FLUID_FIRST;

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, Content.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    private static void transport(GameTestHelper helper, int x) {
        helper.setBlock(new BlockPos(x, 1, 1), Content.TRANSPORT_PIPE.get());
    }

    private static BlockPos barrel(GameTestHelper helper, int x, ItemStack... contents) {
        BlockPos pos = new BlockPos(x, 2, 1);
        helper.setBlock(pos, Blocks.BARREL);
        Container container = helper.getBlockEntity(pos);
        for (int slot = 0; slot < contents.length; slot++) {
            container.setItem(slot, contents[slot]);
        }
        return pos;
    }

    private static ItemStack upgrade(UpgradeKind kind, int count) {
        return new ItemStack(Content.UPGRADES.get(kind).get(), count);
    }

    private static int stepButton(int slot, int step) {
        for (int index = 0; index < PipeConfigMenu.FLUID_STEPS.length; index++) {
            if (PipeConfigMenu.FLUID_STEPS[index] == step) {
                return PipeConfigMenu.BUTTON_FLUID_AMOUNT + slot * PipeConfigMenu.FLUID_STEPS.length + index;
            }
        }
        throw new IllegalArgumentException("no button for " + step);
    }

    // a bucket of lava into the first fluid slot, a bucket a run, sent to the fluid satellite called Lava
    private static void setUpLava(TestPlayer player, PipeConfigMenu menu) {
        menu.setCarried(new ItemStack(Items.LAVA_BUCKET));
        menu.clicked(FIRST_FLUID, 0, ClickType.PICKUP, player);
        menu.setCarried(ItemStack.EMPTY);
        MenuRequests.applyText(player, menu.containerId, SatelliteNames.FIELD_FLUID, "Lava");
        menu.clickMenuButton(player, stepButton(0, 1000));
        menu.clickMenuButton(player, stepButton(0, 10));
        menu.clickMenuButton(player, stepButton(0, -10));
    }

    @GameTest(template = EMPTY)
    public static void theSettingsOfACraftingPipeTakeAFluidItsAmountAndItsSatellite(GameTestHelper helper) {
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 0);
        pipe(helper, PipeKind.FLUID_SATELLITE, 1).setSatelliteName("Lava");
        pipe(helper, PipeKind.SATELLITE, 2).setSatelliteName("Items");
        crafting.config().setItem(0, new ItemStack(Items.COBBLESTONE));
        crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.OBSIDIAN));
        TestPlayer player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            player.openMenu(crafting.menuProvider());
            PipeConfigMenu menu = (PipeConfigMenu) player.containerMenu;
            helper.assertTrue(menu.fluidSlots() == 0 && !menu.getSlot(FIRST_FLUID).isActive(), "no fluid slots without the upgrade");
            helper.assertTrue(!menu.clickMenuButton(player, stepButton(0, 1)), "no amounts without the upgrade");

            crafting.installUpgrade(upgrade(UpgradeKind.FLUID_CRAFTING, 1));
            player.openMenu(crafting.menuProvider());
            menu = (PipeConfigMenu) player.containerMenu;
            helper.assertTrue(menu.fluidSlots() == 1 && menu.getSlot(FIRST_FLUID).isActive() && !menu.getSlot(FIRST_FLUID + 1).isActive(),
                    "one upgrade opens one fluid slot");
            helper.assertTrue(menu.fluidSatellitesInReach().equals(List.of("Lava")), "only fluid satellites to pick: " + menu.fluidSatellitesInReach());
            setUpLava(player, menu);
            helper.assertTrue(menu.fluidAmount(0) == 1000 && menu.fluidSatellite(0).equals("Lava"), "amount and satellite as the menu shows them");

            List<FluidInput> fluids = crafting.crafts().get(0).fluids();
            helper.assertTrue(fluids.size() == 1 && fluids.get(0).fluid().equals(new McFluidType(Fluids.LAVA))
                    && fluids.get(0).amount() == 1000 && fluids.get(0).satellite().equals("Lava"), "the recipe takes the lava: " + fluids);

            menu.clicked(FIRST_FLUID, 1, ClickType.PICKUP, player);
            helper.assertTrue(crafting.crafts().get(0).fluids().isEmpty(), "a right click empties the fluid slot");
            menu.setCarried(new ItemStack(Items.WATER_BUCKET));
            menu.clicked(FIRST_FLUID, 0, ClickType.PICKUP, player);
            menu.setCarried(new ItemStack(Items.COBBLESTONE));
            menu.clicked(FIRST_FLUID, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.fluidMarker(0).isEmpty(), "an item without fluid clears the slot");
            menu.setCarried(new ItemStack(Items.WATER_BUCKET));
            menu.clicked(FIRST_FLUID, 0, ClickType.PICKUP, player);

            PipeBlockEntity loaded = pipe(helper, PipeKind.CRAFTING, 4);
            loaded.installUpgrade(upgrade(UpgradeKind.FLUID_CRAFTING, 1));
            loaded.loadWithComponents(crafting.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            List<FluidInput> saved = loaded.crafts().get(0).fluids();
            helper.assertTrue(saved.size() == 1 && saved.get(0).fluid().equals(new McFluidType(Fluids.WATER)) && saved.get(0).amount() == 1000,
                    "fluid, amount and satellite are saved: " + saved);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void withTheAdvancedSatelliteUpgradeEveryFluidHasItsOwnSatellite(GameTestHelper helper) {
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 0);
        crafting.config().setItem(0, new ItemStack(Items.COBBLESTONE));
        crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.OBSIDIAN));
        crafting.upgrades().setItem(0, upgrade(UpgradeKind.FLUID_CRAFTING, 2));
        crafting.installUpgrade(upgrade(UpgradeKind.ADVANCED_SATELLITE, 1));
        TestPlayer player = TestPlayer.in(helper);

        player.openMenu(crafting.menuProvider());
        PipeConfigMenu menu = (PipeConfigMenu) player.containerMenu;
        for (int slot = 0; slot < 2; slot++) {
            menu.setCarried(new ItemStack(slot == 0 ? Items.LAVA_BUCKET : Items.WATER_BUCKET));
            menu.clicked(FIRST_FLUID + slot, 0, ClickType.PICKUP, player);
            menu.clickMenuButton(player, stepButton(slot, 100));
        }
        MenuRequests.applyText(player, menu.containerId, SatelliteNames.FIELD_FLUID, "Shared");
        MenuRequests.applyText(player, menu.containerId, SatelliteNames.FIELD_FLUID_SLOT, "Lava");
        MenuRequests.applyText(player, menu.containerId, SatelliteNames.FIELD_FLUID_SLOT + 1, "Water");
        MenuRequests.applyText(player, menu.containerId, SatelliteNames.FIELD_FLUID_SLOT + 2, "Third");

        helper.assertTrue(crafting.crafts().get(0).fluids().stream().map(FluidInput::satellite).toList().equals(List.of("Lava", "Water")),
                "every fluid to its own satellite, the shared one and a slot that is not open are refused: " + crafting.crafts().get(0).fluids());
        helper.succeed();
    }

    // request pipe, provider of cobblestone, crafting pipe on a barrel, fluid satellite on a tank, fluid provider on lava
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void aRequestBringsTheItemsToTheCrafterAndTheFluidToItsSatellite(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        transport(helper, 1);
        pipe(helper, PipeKind.PROVIDER, 2);
        barrel(helper, 2, new ItemStack(Items.COBBLESTONE, 8));
        transport(helper, 3);
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 4);
        BlockPos machine = barrel(helper, 4);
        transport(helper, 5);
        pipe(helper, PipeKind.FLUID_SATELLITE, 6).setSatelliteName("Lava");
        TestTank delivered = TestTank.place(helper, new BlockPos(6, 2, 1), Fluids.EMPTY, 0);
        transport(helper, 7);
        pipe(helper, PipeKind.FLUID_PROVIDER, 8);
        TestTank source = TestTank.place(helper, new BlockPos(8, 2, 1), Fluids.LAVA, 3000);
        crafting.config().setItem(0, new ItemStack(Items.COBBLESTONE));
        crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.OBSIDIAN));
        crafting.installUpgrade(upgrade(UpgradeKind.FLUID_CRAFTING, 1));
        TestPlayer player = TestPlayer.in(helper);
        player.openMenu(crafting.menuProvider());
        setUpLava(player, (PipeConfigMenu) player.containerMenu);

        helper.runAfterDelay(SETTLE, () -> {
            RequestOutcome tooMuch = request.request(McItemType.batchOf(new ItemStack(Items.OBSIDIAN, 4)));
            helper.assertTrue(!tooMuch.started() && tooMuch.missing().values().stream().toList().equals(List.of(1000)),
                    "a bucket short for four: " + tooMuch.missing());
            RequestOutcome outcome = request.request(McItemType.batchOf(new ItemStack(Items.OBSIDIAN, 2)));
            helper.assertTrue(outcome.started(), "two can be made, missing " + outcome.missing());
        });

        helper.succeedWhen(() -> {
            Container ingredients = helper.getBlockEntity(machine);
            helper.assertTrue(ingredients.countItem(Items.COBBLESTONE) == 2, "the cobblestone goes to the crafter");
            helper.assertTrue(delivered.amount() == 2000 && delivered.fluid() == Fluids.LAVA,
                    "the lava goes to the fluid satellite, it has " + delivered.amount());
            helper.assertTrue(source.amount() == 1000, "and comes out of the provider's tank");
        });
    }

    @GameTest(template = EMPTY)
    public static void aCrafterModuleTakesFluidWithAnUpgradeInItsOwnSlots(GameTestHelper helper) {
        BlockPos chassisPos = new BlockPos(0, 1, 1);
        helper.setBlock(chassisPos, Content.CHASSIS_PIPES.get(1).get());
        PipeBlockEntity chassis = helper.getBlockEntity(chassisPos);
        pipe(helper, PipeKind.FLUID_SATELLITE, 1).setSatelliteName("Lava");
        ItemStack module = new ItemStack(Content.MODULES.get(ModuleKind.CRAFTER).get());
        ModuleItem.configureCrafter(module, List.of(new ItemStack(Items.COBBLESTONE)), new ItemStack(Items.OBSIDIAN), "", 0);
        chassis.modules().setItem(0, module);
        chassis.moduleUpgrades().setItem(0, upgrade(UpgradeKind.FLUID_CRAFTING, 1));
        TestPlayer player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            player.openMenu(chassis.menuProvider());
            player.containerMenu.clickMenuButton(player, ChassisMenu.BUTTON_SETTINGS);
            PipeConfigMenu menu = (PipeConfigMenu) player.containerMenu;
            helper.assertTrue(menu.fluidSatellitesInReach().equals(List.of("Lava")), "the fluid satellites the chassis reaches");
            setUpLava(player, menu);

            List<FluidInput> fluids = chassis.crafts().get(0).fluids();
            helper.assertTrue(fluids.size() == 1 && fluids.get(0).amount() == 1000 && fluids.get(0).satellite().equals("Lava"),
                    "the module keeps fluid, amount and satellite: " + fluids);
            chassis.moduleUpgrades().setItem(0, ItemStack.EMPTY);
            helper.assertTrue(chassis.crafts().get(0).fluids().isEmpty(), "without the upgrade the recipe takes no fluid");
            helper.assertTrue(UpgradeKind.FLUID_CRAFTING.fitsModule(ModuleKind.CRAFTER) && !UpgradeKind.FLUID_CRAFTING.fitsModule(ModuleKind.PROVIDER)
                    && UpgradeKind.FLUID_CRAFTING.fitsPipe(PipeKind.CRAFTING) && !UpgradeKind.FLUID_CRAFTING.fitsPipe(PipeKind.CHASSIS),
                    "for crafting pipes and crafter modules");
            helper.succeed();
        });
    }
}
