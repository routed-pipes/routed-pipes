package routedpipes.adapter.minecraft.gametest;

import routedpipes.adapter.minecraft.content.Content;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import routedpipes.adapter.minecraft.GuideBookItem;
import routedpipes.adapter.minecraft.GuideBookGift;
import routedpipes.adapter.minecraft.Platform;
import routedpipes.domain.guide.GuideChapter;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class GuideBookGameTest {

    private static final String EMPTY = "empty";

    // the english texts as the client gets them
    private static Set<String> langKeys() {
        try (InputStream in = GuideBookGameTest.class.getResourceAsStream("/assets/routedpipes/lang/en_us.json")) {
            JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return lang.keySet();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Item> modItems() {
        return BuiltInRegistries.ITEM.stream()
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Content.MODID)).toList();
    }

    // the book is made from the registry, so whatever is added later shows up as long as it has a description
    @GameTest(template = EMPTY)
    public static void everyThingOfTheModIsInTheBook(GameTestHelper helper) {
        Set<String> keys = langKeys();
        Map<GuideChapter, List<Item>> book = GuideBookItem.entries(keys::contains);
        List<Item> listed = book.values().stream().flatMap(List::stream).toList();
        List<String> missing = new ArrayList<>();
        for (Item item : modItems()) {
            // never in a player's hands
            if (item == Content.FLUID_CONTAINER.get()) {
                continue;
            }
            if (!keys.contains(item.getDescriptionId()) || !listed.contains(item)) {
                missing.add(BuiltInRegistries.ITEM.getKey(item).getPath());
            }
        }
        helper.assertTrue(missing.isEmpty(), "without a name or a description in the book: " + missing);
        helper.assertTrue(!listed.contains(Content.FLUID_CONTAINER.get()), "the fluid container is left out");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void thingsAreSortedIntoTheirChapters(GameTestHelper helper) {
        Map<GuideChapter, List<Item>> book = GuideBookItem.entries(langKeys()::contains);
        helper.assertTrue(book.get(GuideChapter.PIPES).contains(Content.pipe(PipeKind.CRAFTING).asItem())
                && book.get(GuideChapter.PIPES).contains(Content.TUBES.values().iterator().next().get().asItem()), "pipes and tubes");
        helper.assertTrue(book.get(GuideChapter.MODULES).size() == Content.MODULES.size(), "every module");
        helper.assertTrue(book.get(GuideChapter.UPGRADES).size() == Content.UPGRADES.size(), "every upgrade");
        helper.assertTrue(book.get(GuideChapter.BLOCKS).contains(Content.POWER_JUNCTION.get().asItem()), "blocks");
        helper.assertTrue(book.get(GuideChapter.TOOLS).contains(Content.SIGN_CREATOR.get())
                && book.get(GuideChapter.TOOLS).contains(Content.GUIDE_BOOK.get()), "tools");
        helper.assertTrue(langKeys().contains("routedpipes.guide.start.1"), "and there is a chapter to start with");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theBookIsCraftedFromABookAndAPipe(GameTestHelper helper) {
        ItemStack book = RecipeGameTest.crafted(helper, 2, 1, Items.BOOK, Content.TRANSPORT_PIPE.get().asItem());
        helper.assertTrue(book.is(Content.GUIDE_BOOK.get()) && book.getCount() == 1, "got " + book);
        helper.assertTrue(book.getMaxStackSize() == 1, "books do not stack");
        helper.succeed();
    }

    private static void craft(Player player, ItemStack crafted) {
        GameTestPlatform.get().crafted(player, crafted);
    }

    private static int books(Player player) {
        return player.getInventory().countItem(Content.GUIDE_BOOK.get());
    }

    @GameTest(template = EMPTY)
    public static void theFirstThingOfTheModAPlayerCraftsBringsTheBook(GameTestHelper helper) {
        TestPlayer player = TestPlayer.in(helper);

        craft(player, new ItemStack(Items.STICK));
        helper.assertTrue(books(player) == 0, "crafting something else brings nothing");

        craft(player, new ItemStack(Content.TRANSPORT_PIPE.get(), 8));
        helper.assertTrue(books(player) == 1, "the first pipe brings the book");

        craft(player, new ItemStack(Content.TRANSPORT_PIPE.get(), 8));
        helper.assertTrue(books(player) == 1, "only the first time");
        helper.assertTrue(Platform.get().guideBookGiven(player), "the player is marked");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void craftingTheBookFirstBringsNoSecondOne(GameTestHelper helper) {
        TestPlayer player = TestPlayer.in(helper);

        craft(player, new ItemStack(Content.GUIDE_BOOK.get()));
        craft(player, new ItemStack(Content.TRANSPORT_PIPE.get(), 8));

        helper.assertTrue(books(player) == 0, "the crafted book is all there is, got " + books(player));
        helper.succeed();
    }
}
