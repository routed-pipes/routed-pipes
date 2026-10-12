package logisticspipes.domain.logistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.routing.Side;
import logisticspipes.testing.Fixtures;
import org.junit.jupiter.api.Test;

class SneakyFacesTest {

    private static final ItemType IRON = new Fixtures.TestItem("iron");
    private static final ItemType COAL = new Fixtures.TestItem("coal");
    private static final SneakyFaces.Slot OTHER = SneakyFaces.Slot.OTHER;
    private static final SneakyFaces.Slot COMBINATION = SneakyFaces.Slot.COMBINATION;

    private static SneakyFaces.Slot sneaky(Side face) {
        return SneakyFaces.Slot.sneaky(face);
    }

    @Test
    void withoutUpgradesThePipeUsesTheFaceItTouches() {
        SneakyFaces faces = SneakyFaces.of(List.of(OTHER, OTHER), List.of());

        assertThat(faces.reading(Side.WEST)).isEqualTo(Side.WEST);
        assertThat(faces.inserting(Side.WEST)).containsExactly(Side.WEST);
    }

    @Test
    void theFirstSneakyUpgradeDecidesTheFaceForEverything() {
        SneakyFaces faces = SneakyFaces.of(List.of(OTHER, sneaky(Side.DOWN), sneaky(Side.UP)), List.of());

        assertThat(faces.reading(Side.WEST)).isEqualTo(Side.DOWN);
        assertThat(faces.inserting(Side.WEST)).containsExactly(Side.DOWN);
    }

    @Test
    void combinationTriesTheFacesOfItsRowInOrder() {
        SneakyFaces faces = SneakyFaces.of(List.of(COMBINATION), List.of(Side.UP, Side.NORTH, Side.DOWN));

        assertThat(faces.inserting(Side.WEST)).containsExactly(Side.UP, Side.NORTH, Side.DOWN);
    }

    @Test
    void combinationStillReadsThroughTheFaceThePipeTouches() {
        SneakyFaces faces = SneakyFaces.of(List.of(COMBINATION), List.of(Side.UP, Side.DOWN));

        assertThat(faces.reading(Side.WEST)).isEqualTo(Side.WEST);
    }

    @Test
    void combinationWithAnEmptyRowChangesNothing() {
        SneakyFaces faces = SneakyFaces.of(List.of(COMBINATION), List.of());

        assertThat(faces.inserting(Side.WEST)).containsExactly(Side.WEST);
    }

    @Test
    void ofSneakyAndCombinationTheEarlierSlotCounts() {
        List<Side> row = List.of(Side.UP, Side.DOWN);

        assertThat(SneakyFaces.of(List.of(sneaky(Side.NORTH), COMBINATION), row).inserting(Side.WEST)).containsExactly(Side.NORTH);
        assertThat(SneakyFaces.of(List.of(COMBINATION, sneaky(Side.NORTH)), row).inserting(Side.WEST)).containsExactly(Side.UP, Side.DOWN);
        assertThat(SneakyFaces.of(List.of(COMBINATION, sneaky(Side.NORTH)), row).reading(Side.WEST)).isEqualTo(Side.WEST);
    }

    @Test
    void aModuleWithItsOwnSneakyUpgradeIgnoresTheRowOfThePipe() {
        SneakyFaces pipe = SneakyFaces.of(List.of(COMBINATION), List.of(Side.UP, Side.DOWN));

        assertThat(pipe.forModule(Optional.of(Side.SOUTH)).inserting(Side.WEST)).containsExactly(Side.SOUTH);
        assertThat(pipe.forModule(Optional.empty()).inserting(Side.WEST)).containsExactly(Side.UP, Side.DOWN);
    }

    @Test
    void theModuleThatTakesTheItemDecidesTheFace() {
        List<SneakyFaces.ModuleFace> modules = List.of(
                new SneakyFaces.ModuleFace(new ItemSink(Set.of(IRON), false), Optional.of(Side.UP)),
                new SneakyFaces.ModuleFace(new ItemSink(Set.of(COAL), false), Optional.of(Side.NORTH)));

        assertThat(SneakyFaces.ofModuleTaking(IRON, Map.of(), modules)).contains(Side.UP);
        assertThat(SneakyFaces.ofModuleTaking(COAL, Map.of(), modules)).contains(Side.NORTH);
        assertThat(SneakyFaces.ofModuleTaking(new Fixtures.TestItem("dirt"), Map.of(), modules)).isEmpty();
    }

    @Test
    void theKeenestModuleWinsAndTheEarlierSlotOnATie() {
        SneakyFaces.ModuleFace defaultRoute = new SneakyFaces.ModuleFace(new ItemSink(Set.of(), true), Optional.of(Side.DOWN));
        SneakyFaces.ModuleFace listed = new SneakyFaces.ModuleFace(new ItemSink(Set.of(IRON), false), Optional.of(Side.UP));
        SneakyFaces.ModuleFace listedToo = new SneakyFaces.ModuleFace(new ItemSink(Set.of(IRON), false), Optional.of(Side.NORTH));

        assertThat(SneakyFaces.ofModuleTaking(IRON, Map.of(), List.of(defaultRoute, listed, listedToo))).contains(Side.UP);
        assertThat(SneakyFaces.ofModuleTaking(COAL, Map.of(), List.of(defaultRoute, listed))).contains(Side.DOWN);
    }

    @Test
    void aWinningModuleWithoutSneakyUpgradeLeavesTheFaceToThePipe() {
        List<SneakyFaces.ModuleFace> modules = List.of(
                new SneakyFaces.ModuleFace(new ItemSink(Set.of(IRON), false), Optional.empty()),
                new SneakyFaces.ModuleFace(new ItemSink(Set.of(), true), Optional.of(Side.DOWN)));

        assertThat(SneakyFaces.ofModuleTaking(IRON, Map.of(), modules)).isEmpty();
    }
}
