package routedpipes.adapter.neoforge.gametest;

import routedpipes.adapter.minecraft.content.Content;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// worlds saved while the mod still had the id of the original keep loading
@GameTestHolder(Content.MODID)
@PrefixGameTestTemplate(false)
public class OldIdGameTest {

    private static final String EMPTY = "empty";
    private static final String OLD = "logisticspipes:";

    @GameTest(template = EMPTY)
    public static void aSavedItemWithTheOldIdIsStillThatItem(GameTestHelper helper) {
        CompoundTag saved = new CompoundTag();
        saved.putString("id", OLD + "basic_logistics_pipe");
        saved.putInt("count", 3);

        ItemStack loaded = ItemStack.parseOptional(helper.getLevel().registryAccess(), saved);

        helper.assertTrue(loaded.is(Content.pipe(PipeKind.BASIC).asItem()) && loaded.getCount() == 3, "got " + loaded);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void whatASavedItemCarriesUnderTheOldIdIsKept(GameTestHelper helper) {
        ItemStack original = new ItemStack(Content.SIGN_CREATOR.get());
        original.set(routedpipes.adapter.minecraft.SignCreatorItem.MODE, 2);
        CompoundTag saved = (CompoundTag) original.save(helper.getLevel().registryAccess());
        CompoundTag components = saved.getCompound("components");
        CompoundTag renamed = new CompoundTag();
        components.getAllKeys().forEach(key -> renamed.put(key.replace(Content.MODID + ":", OLD), components.get(key)));
        saved.put("components", renamed);
        saved.putString("id", saved.getString("id").replace(Content.MODID + ":", OLD));
        helper.assertTrue(renamed.getAllKeys().stream().anyMatch(key -> key.startsWith(OLD)), "the test saves under the old id, got " + saved);

        ItemStack loaded = ItemStack.parseOptional(helper.getLevel().registryAccess(), saved);

        helper.assertTrue(ItemStack.isSameItemSameComponents(loaded, original), "got " + loaded + " with " + loaded.getComponentsPatch());
        helper.assertTrue(!loaded.has(DataComponents.CUSTOM_DATA), "nothing ends up as loose data");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aSavedBlockWithTheOldIdIsStillThatBlock(GameTestHelper helper) {
        CompoundTag saved = new CompoundTag();
        saved.putString("Name", OLD + "basic_logistics_pipe");

        BlockState loaded = NbtUtils.readBlockState(helper.getLevel().holderLookup(Registries.BLOCK), saved);

        helper.assertTrue(loaded.is(Content.pipe(PipeKind.BASIC)), "got " + loaded);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aSavedBlockEntityWithTheOldIdIsLoaded(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Content.pipe(PipeKind.BASIC));
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        CompoundTag saved = pipe.saveWithFullMetadata(helper.getLevel().registryAccess());
        saved.putString("id", saved.getString("id").replace(Content.MODID + ":", OLD));
        helper.assertTrue(saved.getString("id").startsWith(OLD), "the test saves under the old id, got " + saved.getString("id"));

        BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(pos), pipe.getBlockState(), saved, helper.getLevel().registryAccess());

        helper.assertTrue(loaded instanceof PipeBlockEntity, "got " + loaded);
        helper.succeed();
    }
}
