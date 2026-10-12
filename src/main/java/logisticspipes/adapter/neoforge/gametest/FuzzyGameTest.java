package logisticspipes.adapter.neoforge.gametest;

import java.util.List;

import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.ModuleItem;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.menu.ChassisMenu;
import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import logisticspipes.domain.item.FuzzyFlags;
import logisticspipes.domain.item.FuzzyType;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.ModuleKind;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.UpgradeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class FuzzyGameTest {

    private static final String EMPTY = "empty";
    private static final FuzzyFlags TAG = new FuzzyFlags(true, false, false, false);
    private static final FuzzyFlags IGNORE_DAMAGE = new FuzzyFlags(false, true, false, false);
    private static final int SETTLE = 15;

    private static ItemStack upgrade(UpgradeKind kind) {
        return new ItemStack(LogisticsPipes.UPGRADES.get(kind).get());
    }

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, LogisticsPipes.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    private static Container chest(GameTestHelper helper, int x, ItemStack... contents) {
        BlockPos pos = new BlockPos(x, 2, 1);
        helper.setBlock(pos, Blocks.CHEST);
        Container container = helper.getBlockEntity(pos);
        for (int slot = 0; slot < contents.length; slot++) {
            container.setItem(slot, contents[slot]);
        }
        return container;
    }

    private static ItemStack wornPickaxe() {
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        pickaxe.setDamageValue(40);
        return pickaxe;
    }

    private static ItemType type(ItemStack stack) {
        return McItemType.batchOf(stack).type();
    }

    // what the flags look at comes out of real stacks the way the matching rule expects it
    @GameTest(template = EMPTY)
    public static void itemTypesKnowTheirDamageAndTags(GameTestHelper helper) {
        ItemType fresh = type(new ItemStack(Items.IRON_PICKAXE));
        ItemType worn = type(wornPickaxe());

        helper.assertTrue(fresh.damage() == 0 && worn.damage() == 40, "damage of a stack");
        helper.assertTrue(worn.undamaged().equals(fresh) && fresh.undamaged().equals(fresh), "without the damage it is a fresh one");
        helper.assertTrue(type(new ItemStack(Items.OAK_PLANKS)).tags().contains("minecraft:planks"), "tags of an item");
        helper.assertTrue(IGNORE_DAMAGE.matches(fresh, worn) && !FuzzyFlags.NONE.matches(fresh, worn), "a worn pickaxe matches a fresh one");
        helper.assertTrue(TAG.matches(type(new ItemStack(Items.OAK_PLANKS)), type(new ItemStack(Items.BIRCH_PLANKS))), "planks share a tag");
        helper.assertTrue(!TAG.matches(type(new ItemStack(Items.OAK_PLANKS)), type(new ItemStack(Items.COBBLESTONE))), "planks and stone do not");
        helper.succeed();
    }

    // a basic pipe listing oak planks and a fresh pickaxe at x 0, a default route at x 1 where the items start
    private static PipeBlockEntity listingPipe(GameTestHelper helper) {
        PipeBlockEntity listing = pipe(helper, PipeKind.BASIC, 0);
        listing.config().setItem(0, new ItemStack(Items.OAK_PLANKS));
        listing.config().setItem(1, new ItemStack(Items.IRON_PICKAXE));
        listing.slotSettings().setFuzzyFlags(0, TAG);
        listing.slotSettings().setFuzzyFlags(1, IGNORE_DAMAGE);
        return listing;
    }

    private static void sendFromDefaultRoute(GameTestHelper helper) {
        PipeBlockEntity start = pipe(helper, PipeKind.BASIC, 1);
        start.setDefaultRoute(true);
        helper.runAfterDelay(SETTLE, () -> {
            start.accept(new ItemStack(Items.BIRCH_PLANKS, 8), Direction.DOWN);
            start.accept(wornPickaxe(), Direction.DOWN);
            start.accept(new ItemStack(Items.COBBLESTONE, 3), Direction.DOWN);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void basicPipeWithFuzzyUpgradeTakesWhatMatchesItsList(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity listing = listingPipe(helper);
        helper.assertTrue(listing.installUpgrade(upgrade(UpgradeKind.FUZZY)), "a basic pipe takes the fuzzy upgrade");
        Container listed = chest(helper, 0);
        Container rest = chest(helper, 1);
        sendFromDefaultRoute(helper);

        helper.runAfterDelay(100, () -> {
            helper.assertTrue(listed.countItem(Items.BIRCH_PLANKS) == 8, "birch planks pass for oak planks");
            helper.assertTrue(listed.countItem(Items.IRON_PICKAXE) == 1, "a worn pickaxe passes for a fresh one");
            helper.assertTrue(rest.countItem(Items.COBBLESTONE) == 3 && listed.countItem(Items.COBBLESTONE) == 0, "stone matches nothing");
            helper.succeed();
        });
    }

    // the flags are kept but do nothing
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void withoutTheFuzzyUpgradeFlagsAreIgnored(GameTestHelper helper) {
        GameTestPower.supply(helper);
        listingPipe(helper);
        Container listed = chest(helper, 0);
        Container rest = chest(helper, 1);
        sendFromDefaultRoute(helper);

        helper.runAfterDelay(100, () -> {
            helper.assertTrue(listed.isEmpty(), "nothing matches exactly");
            helper.assertTrue(rest.countItem(Items.BIRCH_PLANKS) == 8 && rest.countItem(Items.IRON_PICKAXE) == 1, "all of it stays");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void itemSinkModuleWithFuzzyUpgradeTakesWhatMatchesItsList(GameTestHelper helper) {
        GameTestPower.supply(helper);
        BlockPos chassisPos = new BlockPos(0, 1, 1);
        helper.setBlock(chassisPos, LogisticsPipes.CHASSIS_PIPES.get(1).get());
        PipeBlockEntity chassis = helper.getBlockEntity(chassisPos);
        ItemStack module = new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.ITEM_SINK).get());
        ModuleItem.configureSink(module, List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.IRON_PICKAXE)), false);
        chassis.modules().setItem(0, module);
        chassis.moduleUpgrades().setItem(0, upgrade(UpgradeKind.FUZZY));
        // the flags are set in the settings of the module, opened from the chassis
        TestPlayer player = TestPlayer.in(helper);
        player.openMenu(chassis.menuProvider());
        player.containerMenu.clickMenuButton(player, ChassisMenu.BUTTON_SETTINGS);
        helper.assertTrue(player.containerMenu.clickMenuButton(player, PipeConfigMenu.BUTTON_FUZZY), "tag flag of the first slot");
        helper.assertTrue(player.containerMenu.clickMenuButton(player, PipeConfigMenu.BUTTON_FUZZY + FuzzyFlags.COUNT + 1),
                "damage flag of the second slot");
        Container listed = chest(helper, 0);
        Container rest = chest(helper, 1);
        sendFromDefaultRoute(helper);

        helper.runAfterDelay(100, () -> {
            helper.assertTrue(listed.countItem(Items.BIRCH_PLANKS) == 8 && listed.countItem(Items.IRON_PICKAXE) == 1,
                    "planks and pickaxe match the module");
            helper.assertTrue(rest.countItem(Items.COBBLESTONE) == 3, "stone matches nothing");
            helper.succeed();
        });
    }

    // sticks from any two planks: the provider only has birch, and that is what the crafting pipe gets
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void craftingIngredientWithFlagsIsServedByAnyMatchingItem(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 0);
        crafting.config().setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
        crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.STICK, 4));
        crafting.slotSettings().setFuzzyFlags(0, TAG);
        Container machine = chest(helper, 0);
        pipe(helper, PipeKind.PROVIDER, 1);
        chest(helper, 1, new ItemStack(Items.BIRCH_PLANKS, 16));
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 2);
        ItemStack sticks = new ItemStack(Items.STICK, 8);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(!request.request(McItemType.batchOf(sticks)).started(), "without the upgrade only oak will do");
            helper.assertTrue(crafting.installUpgrade(upgrade(UpgradeKind.FUZZY)), "a crafting pipe takes the fuzzy upgrade");
            helper.assertTrue(crafting.recipe().orElseThrow().inputs().get(0).type() instanceof FuzzyType, "now the flags count");
            helper.assertTrue(request.request(McItemType.batchOf(sticks)).started(), "with it birch will do");
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(machine.countItem(Items.BIRCH_PLANKS) == 4, "four birch planks for two runs, found "
                    + machine.countItem(Items.BIRCH_PLANKS));
            helper.succeed();
        });
    }

    // the machine of this crafter puts out worn pickaxes, a request for a fresh one gets one of those
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void craftingResultWithFlagsAnswersRequestsWithWhateverMatches(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 0);
        crafting.config().setItem(0, new ItemStack(Items.COBBLESTONE));
        crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.IRON_PICKAXE));
        crafting.slotSettings().setFuzzyFlags(PipeBlockEntity.OUTPUT_SLOT, IGNORE_DAMAGE);
        crafting.installUpgrade(upgrade(UpgradeKind.FUZZY));
        chest(helper, 0, wornPickaxe());
        pipe(helper, PipeKind.PROVIDER, 1);
        chest(helper, 1, new ItemStack(Items.COBBLESTONE, 16));
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 2);
        Container target = chest(helper, 2);
        ItemStack fresh = new ItemStack(Items.IRON_PICKAXE);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(request.available().containsKey(type(fresh)), "the pickaxe is offered as the item in the result slot");
            helper.assertTrue(request.request(McItemType.batchOf(fresh)).started(), "the crafter answers");
            // orders with flags survive saving
            PipeBlockEntity loaded = pipe(helper, PipeKind.CRAFTING, 4);
            loaded.loadWithComponents(crafting.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertTrue(loaded.outputOrders().all().get(0).batch().type() instanceof FuzzyType, "the order keeps its flags");
            helper.setBlock(new BlockPos(4, 1, 1), Blocks.AIR);
        });
        helper.runAfterDelay(120, () -> {
            ItemStack delivered = target.getItem(0);
            helper.assertTrue(delivered.is(Items.IRON_PICKAXE) && delivered.getDamageValue() == 40, "the worn pickaxe arrived, found " + delivered);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void settingsScreenSetsFlagsOnlyWithTheUpgrade(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 0);
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 1);
        TestPlayer player = TestPlayer.in(helper);
        int resultDamage = PipeConfigMenu.BUTTON_FUZZY + PipeBlockEntity.OUTPUT_SLOT * FuzzyFlags.COUNT + 1;

        player.openMenu(crafting.menuProvider());
        helper.assertTrue(!player.containerMenu.clickMenuButton(player, PipeConfigMenu.BUTTON_FUZZY), "no flags without the upgrade");

        crafting.installUpgrade(upgrade(UpgradeKind.FUZZY));
        basic.installUpgrade(upgrade(UpgradeKind.FUZZY));
        player.openMenu(crafting.menuProvider());
        PipeConfigMenu menu = (PipeConfigMenu) player.containerMenu;
        helper.assertTrue(menu.fuzzySlots() == 10, "nine ingredients and the result");
        menu.clickMenuButton(player, PipeConfigMenu.BUTTON_FUZZY + 2 * FuzzyFlags.COUNT);
        menu.clickMenuButton(player, PipeConfigMenu.BUTTON_FUZZY + 2 * FuzzyFlags.COUNT + 3);
        menu.clickMenuButton(player, resultDamage);
        helper.assertTrue(crafting.slotSettings().fuzzyFlags(2).equals(new FuzzyFlags(true, false, false, true)), "two flags on the third slot");
        helper.assertTrue(menu.fuzzyFlags(PipeBlockEntity.OUTPUT_SLOT).equals(IGNORE_DAMAGE), "one on the result");
        menu.clickMenuButton(player, PipeConfigMenu.BUTTON_FUZZY + 2 * FuzzyFlags.COUNT);
        helper.assertTrue(crafting.slotSettings().fuzzyFlags(2).equals(new FuzzyFlags(false, false, false, true)), "a second click takes it off");

        player.openMenu(basic.menuProvider());
        menu = (PipeConfigMenu) player.containerMenu;
        helper.assertTrue(menu.fuzzySlots() == 9 && !menu.clickMenuButton(player, resultDamage), "a basic pipe has no result slot");

        PipeBlockEntity loaded = pipe(helper, PipeKind.CRAFTING, 3);
        loaded.loadWithComponents(crafting.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(loaded.slotSettings().fuzzyFlags(PipeBlockEntity.OUTPUT_SLOT).equals(IGNORE_DAMAGE), "flags are saved");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void crafterModuleUsesItsFlagsOnlyWithAFuzzyUpgradeOfItsOwn(GameTestHelper helper) {
        GameTestPower.supply(helper);
        BlockPos chassisPos = new BlockPos(0, 1, 1);
        helper.setBlock(chassisPos, LogisticsPipes.CHASSIS_PIPES.get(1).get());
        PipeBlockEntity chassis = helper.getBlockEntity(chassisPos);
        ItemStack module = new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.CRAFTER).get());
        ModuleItem.configureCrafter(module, List.of(new ItemStack(Items.OAK_PLANKS, 2)), new ItemStack(Items.STICK, 4), 0, 0);
        chassis.modules().setItem(0, module);
        chassis.moduleUpgrades().setItem(0, upgrade(UpgradeKind.FUZZY));
        TestPlayer player = TestPlayer.in(helper);

        player.openMenu(chassis.menuProvider());
        player.containerMenu.clickMenuButton(player, ChassisMenu.BUTTON_SETTINGS);
        helper.assertTrue(player.containerMenu.clickMenuButton(player, PipeConfigMenu.BUTTON_FUZZY), "tag flag of the first ingredient");
        helper.assertTrue(chassis.crafts().get(0).recipe().inputs().get(0).type() instanceof FuzzyType, "the ingredient takes any planks");

        chassis.moduleUpgrades().setItem(0, ItemStack.EMPTY);
        helper.assertTrue(chassis.crafts().get(0).recipe().inputs().get(0).type().equals(type(new ItemStack(Items.OAK_PLANKS))),
                "without the upgrade it is oak again");
        helper.assertTrue(UpgradeKind.FUZZY.fitsModule(ModuleKind.CRAFTER) && UpgradeKind.FUZZY.fitsModule(ModuleKind.ITEM_SINK)
                && !UpgradeKind.FUZZY.fitsModule(ModuleKind.TERMINUS), "for crafter and item sink modules");
        helper.assertTrue(UpgradeKind.FUZZY.fitsPipe(PipeKind.CRAFTING) && UpgradeKind.FUZZY.fitsPipe(PipeKind.BASIC)
                && !UpgradeKind.FUZZY.fitsPipe(PipeKind.PROVIDER), "for crafting and basic pipes");
        helper.succeed();
    }
}
