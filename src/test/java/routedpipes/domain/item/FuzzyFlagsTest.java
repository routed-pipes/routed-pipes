package routedpipes.domain.item;

import static org.assertj.core.api.Assertions.assertThat;

import routedpipes.testing.Fixtures.WornItem;
import org.junit.jupiter.api.Test;

class FuzzyFlagsTest {

    private static final FuzzyFlags TAG = new FuzzyFlags(true, false, false, false);
    private static final FuzzyFlags IGNORE_DAMAGE = new FuzzyFlags(false, true, false, false);
    private static final FuzzyFlags IGNORE_DATA = new FuzzyFlags(false, false, true, false);
    private static final FuzzyFlags TAG_FAMILY = new FuzzyFlags(false, false, false, true);

    private static final WornItem PICKAXE = WornItem.fresh("pickaxe", "minecraft:pickaxes");
    private static final WornItem SHOVEL = WornItem.fresh("shovel", "minecraft:shovels");
    private static final WornItem OAK = WornItem.fresh("oak_planks", "minecraft:planks");
    private static final WornItem BIRCH = WornItem.fresh("birch_planks", "minecraft:planks");
    private static final WornItem IRON = WornItem.fresh("iron_ingot", "c:ingots/iron");
    private static final WornItem COPPER = WornItem.fresh("copper_ingot", "c:ingots/copper");
    private static final WornItem BRICK = WornItem.fresh("brick", "c:ingots");
    private static final WornItem IRON_ORE = WornItem.fresh("iron_ore", "c:ores/iron");

    @Test
    void withoutFlagsOnlyTheVerySameItemMatches() {
        assertThat(FuzzyFlags.NONE.matches(PICKAXE, PICKAXE)).isTrue();
        assertThat(FuzzyFlags.NONE.matches(PICKAXE, PICKAXE.worn(3))).isFalse();
        assertThat(FuzzyFlags.NONE.matches(PICKAXE, PICKAXE.carrying("sharp"))).isFalse();
        assertThat(FuzzyFlags.NONE.matches(OAK, BIRCH)).isFalse();
    }

    @Test
    void tagFlagTakesAnythingSharingATag() {
        assertThat(TAG.matches(OAK, BIRCH)).isTrue();
        assertThat(TAG.matches(OAK, PICKAXE)).isFalse();
        assertThat(TAG.matches(IRON, COPPER)).isFalse();
    }

    @Test
    void anItemWithoutTagsSharesNone() {
        WornItem plain = WornItem.fresh("thing");

        assertThat(TAG.matches(plain, WornItem.fresh("other"))).isFalse();
        assertThat(TAG.matches(plain, plain.worn(2))).isFalse();
    }

    @Test
    void tagFamilyTakesAnythingWithATagOfTheSameFamily() {
        assertThat(TAG_FAMILY.matches(IRON, COPPER)).isTrue();
        assertThat(TAG_FAMILY.matches(IRON, BRICK)).isTrue();
        assertThat(TAG_FAMILY.matches(IRON, IRON_ORE)).isFalse();
        assertThat(TAG_FAMILY.matches(PICKAXE, SHOVEL)).isFalse();
    }

    @Test
    void tagFamilyAlsoTakesWhatSharesAWholeTag() {
        assertThat(TAG_FAMILY.matches(OAK, BIRCH)).isTrue();
    }

    @Test
    void theFamilyIsTheTagUpToItsFirstSlash() {
        assertThat(FuzzyFlags.family("c:ingots/iron")).isEqualTo("c:ingots");
        assertThat(FuzzyFlags.family("c:ingots")).isEqualTo("c:ingots");
        assertThat(FuzzyFlags.family("c:storage_blocks/raw/iron")).isEqualTo("c:storage_blocks");
    }

    @Test
    void ignoreDamageTakesTheSameItemHoweverWorn() {
        assertThat(IGNORE_DAMAGE.matches(PICKAXE, PICKAXE.worn(7))).isTrue();
        assertThat(IGNORE_DAMAGE.matches(PICKAXE.worn(7), PICKAXE)).isTrue();
        assertThat(IGNORE_DAMAGE.matches(PICKAXE, SHOVEL)).isFalse();
    }

    @Test
    void ignoreDamageStillComparesTheRestOfTheData() {
        assertThat(IGNORE_DAMAGE.matches(PICKAXE, PICKAXE.worn(7).carrying("sharp"))).isFalse();
        assertThat(IGNORE_DAMAGE.matches(PICKAXE.carrying("sharp"), PICKAXE.worn(7).carrying("sharp"))).isTrue();
    }

    @Test
    void ignoreDataTakesTheSameItemWhateverItCarries() {
        assertThat(IGNORE_DATA.matches(PICKAXE, PICKAXE.carrying("sharp"))).isTrue();
        assertThat(IGNORE_DATA.matches(PICKAXE, SHOVEL.carrying("sharp"))).isFalse();
    }

    @Test
    void ignoreDataAloneStillWantsTheSameDamage() {
        assertThat(IGNORE_DATA.matches(PICKAXE, PICKAXE.worn(7).carrying("sharp"))).isFalse();
        assertThat(new FuzzyFlags(false, true, true, false).matches(PICKAXE, PICKAXE.worn(7).carrying("sharp"))).isTrue();
    }

    @Test
    void flagsSurviveBeingPackedIntoBits() {
        for (int bits = 0; bits < 16; bits++) {
            assertThat(FuzzyFlags.of(bits).bits()).isEqualTo(bits);
        }
        assertThat(FuzzyFlags.of(1)).isEqualTo(TAG);
        assertThat(FuzzyFlags.of(2)).isEqualTo(IGNORE_DAMAGE);
        assertThat(FuzzyFlags.of(4)).isEqualTo(IGNORE_DATA);
        assertThat(FuzzyFlags.of(8)).isEqualTo(TAG_FAMILY);
        assertThat(FuzzyFlags.NONE.any()).isFalse();
        assertThat(TAG.any()).isTrue();
    }

    @Test
    void aTypeWithFlagsAcceptsWhatItsFlagsLetPass() {
        FuzzyType anyPlanks = new FuzzyType(OAK, TAG);

        assertThat(anyPlanks.accepts(BIRCH)).isTrue();
        assertThat(anyPlanks.accepts(PICKAXE)).isFalse();
        assertThat(anyPlanks.exact()).isEqualTo(OAK);
        assertThat(OAK.accepts(BIRCH)).isFalse();
        assertThat(OAK.accepts(OAK)).isTrue();
        assertThat(OAK.exact()).isSameAs(OAK);
    }
}
