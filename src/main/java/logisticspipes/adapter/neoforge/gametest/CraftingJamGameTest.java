package logisticspipes.adapter.neoforge.gametest;

import java.util.List;

import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.ModuleItem;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import logisticspipes.domain.logistics.ModuleKind;
import logisticspipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// one machine with two recipes: what is brought for one recipe must not get in the way of the other
@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class CraftingJamGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 15;

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

    private static ItemStack bottle(Holder<Potion> potion) {
        return PotionContents.createItemStack(Items.POTION, potion);
    }

    private static int count(Container container, Holder<Potion> potion) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            PotionContents contents = container.getItem(slot).get(DataComponents.POTION_CONTENTS);
            if (contents != null && contents.is(potion)) {
                count += container.getItem(slot).getCount();
            }
        }
        return count;
    }

    private static ItemStack brewing(ItemStack bottle, ItemStack ingredient, ItemStack result) {
        ItemStack module = new ItemStack(LogisticsPipes.MODULES.get(ModuleKind.CRAFTER).get());
        ModuleItem.configureCrafter(module, List.of(bottle.copy(), bottle.copy(), bottle.copy(), ingredient), result.copyWithCount(3), "", 0);
        return module;
    }

    // a brewing stand below which a chassis brews thick potions out of water and glowstone, and swiftness out of
    // awkward potions and sugar. the awkward potions and the glowstone are stored right next to it, the water and
    // the sugar far away. glowstone does nothing to an awkward potion, so if those two meet in the stand
    // nothing is brewed, and neither the water nor the sugar get in any more
    @GameTest(template = EMPTY, timeoutTicks = 1600)
    public static void twoRecipesOrderedAtOnceDoNotJamTheMachineTheyShare(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        Container target = chest(helper, 0);
        pipe(helper, PipeKind.PROVIDER, 1);
        chest(helper, 1, bottle(Potions.AWKWARD), bottle(Potions.AWKWARD), bottle(Potions.AWKWARD), new ItemStack(Items.GLOWSTONE_DUST));
        BlockPos chassisPos = new BlockPos(2, 1, 1);
        helper.setBlock(chassisPos, LogisticsPipes.CHASSIS_PIPES.get(2).get());
        PipeBlockEntity chassis = helper.getBlockEntity(chassisPos);
        chassis.modules().setItem(0, brewing(bottle(Potions.WATER), new ItemStack(Items.GLOWSTONE_DUST), bottle(Potions.THICK)));
        chassis.modules().setItem(1, brewing(bottle(Potions.AWKWARD), new ItemStack(Items.SUGAR), bottle(Potions.SWIFTNESS)));
        BlockPos standPos = new BlockPos(2, 2, 1);
        helper.setBlock(standPos, Blocks.BREWING_STAND);
        Container stand = helper.getBlockEntity(standPos);
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER, 8));
        for (int x = 3; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), LogisticsPipes.TRANSPORT_PIPE.get());
        }
        pipe(helper, PipeKind.PROVIDER, 6);
        chest(helper, 6, bottle(Potions.WATER), bottle(Potions.WATER), bottle(Potions.WATER), new ItemStack(Items.SUGAR));

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(chassis.crafts().size() == 2, "the chassis should know two recipes, knows " + chassis.crafts().size());
            helper.assertTrue(request.request(McItemType.batchOf(bottle(Potions.THICK).copyWithCount(3))).started(), "thick potions can be had");
            helper.assertTrue(request.request(McItemType.batchOf(bottle(Potions.SWIFTNESS).copyWithCount(3))).started(), "swiftness potions can be had");
        });

        helper.succeedWhen(() -> {
            String inStand = "";
            for (int slot = 0; slot < 4; slot++) {
                inStand += " " + stand.getItem(slot).getHoverName().getString();
            }
            helper.assertTrue(count(target, Potions.THICK) == 3 && count(target, Potions.SWIFTNESS) == 3,
                    "delivered " + count(target, Potions.THICK) + " thick and " + count(target, Potions.SWIFTNESS)
                            + " swiftness potions, the stand holds:" + inStand + ", in the pipe below it wait "
                            + chassis.travellingItems().size() + " items");
        });
    }
}
