package routedpipes.domain.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import routedpipes.domain.item.FuzzyFlags;
import routedpipes.domain.item.FuzzyType;
import routedpipes.domain.item.ItemType;
import routedpipes.testing.Fixtures.WornItem;
import org.junit.jupiter.api.Test;

class FuzzyMatchTest {

    private static final FuzzyFlags TAG = new FuzzyFlags(true, false, false, false);
    private static final WornItem OAK = WornItem.fresh("oak_planks", "planks");
    private static final WornItem BIRCH = WornItem.fresh("birch_planks", "planks");
    private static final WornItem SPRUCE = WornItem.fresh("spruce_planks", "planks");
    private static final WornItem STONE = WornItem.fresh("stone");
    private static final ItemType ANY_PLANKS = new FuzzyType(OAK, TAG);

    @Test
    void aTypeWithoutFlagsIsItsOwnOnlyCandidate() {
        assertThat(FuzzyMatch.candidates(OAK, () -> List.<ItemType>of(BIRCH, OAK))).containsExactly(OAK);
    }

    @Test
    void aTypeWithFlagsExpandsToWhatMatchesInStock() {
        List<ItemType> stocked = List.of(STONE, BIRCH, OAK, SPRUCE);

        assertThat(FuzzyMatch.candidates(ANY_PLANKS, () -> stocked)).containsExactly(OAK, BIRCH, SPRUCE);
    }

    @Test
    void theItemOfTheSlotIsACandidateEvenWhenOutOfStock() {
        assertThat(FuzzyMatch.candidates(ANY_PLANKS, () -> List.<ItemType>of(STONE))).containsExactly(OAK);
    }

    @Test
    void aCrafterMakesWhatItsResultNamesOrLetsPass() {
        assertThat(FuzzyMatch.makes(OAK, OAK)).isTrue();
        assertThat(FuzzyMatch.makes(OAK, BIRCH)).isFalse();
        // flags on the result: asked for anything matching
        assertThat(FuzzyMatch.makes(ANY_PLANKS, BIRCH)).isTrue();
        assertThat(FuzzyMatch.makes(ANY_PLANKS, STONE)).isFalse();
        // flags on what is wanted: any crafter making a matching item
        assertThat(FuzzyMatch.makes(BIRCH, ANY_PLANKS)).isTrue();
        assertThat(FuzzyMatch.makes(STONE, ANY_PLANKS)).isFalse();
        assertThat(FuzzyMatch.makes(new FuzzyType(BIRCH, TAG), ANY_PLANKS)).isTrue();
    }

    @Test
    void anOrderWithFlagsIsFilledWithTheMatchingItemThereIsMostOf() {
        Map<ItemType, Integer> contents = new LinkedHashMap<>();
        contents.put(STONE, 64);
        contents.put(BIRCH, 5);
        contents.put(SPRUCE, 9);
        contents.put(OAK, 2);

        assertThat(FuzzyMatch.largest(ANY_PLANKS, contents)).isEqualTo(SPRUCE);
        assertThat(FuzzyMatch.largest(OAK, contents)).isEqualTo(OAK);
        assertThat(FuzzyMatch.largest(ANY_PLANKS, Map.of(STONE, 64))).isEqualTo(OAK);
    }

    @Test
    void amountsAreReportedByTheItemOfTheSlot() {
        Map<ItemType, Integer> amounts = new LinkedHashMap<>();
        amounts.put(ANY_PLANKS, 3);
        amounts.put(OAK, 2);
        amounts.put(STONE, 1);

        assertThat(FuzzyMatch.exact(amounts)).containsOnly(Map.entry(OAK, 5), Map.entry(STONE, 1));
    }
}
