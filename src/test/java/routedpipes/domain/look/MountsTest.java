package routedpipes.domain.look;

import static routedpipes.domain.routing.Side.DOWN;
import static routedpipes.domain.routing.Side.EAST;
import static routedpipes.domain.routing.Side.NORTH;
import static routedpipes.domain.routing.Side.SOUTH;
import static routedpipes.domain.routing.Side.UP;
import static routedpipes.domain.routing.Side.WEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import routedpipes.domain.routing.Side;
import org.junit.jupiter.api.Test;

class MountsTest {

    private static final Set<Side> NOTHING = Set.of();
    private static final Set<Side> EVERYTHING = EnumSet.allOf(Side.class);

    private static Mount mount(Side towards, Side flank) {
        return new Mount(towards, flank);
    }

    private static Set<Side> sides(int bits) {
        Set<Side> sides = EnumSet.noneOf(Side.class);
        for (Side side : Side.values()) {
            if ((bits & 1 << side.ordinal()) != 0) {
                sides.add(side);
            }
        }
        return sides;
    }

    @Test
    void thereAreTwentyFourPlacesInTheOrderOfTheOriginal() {
        assertThat(Mount.all()).hasSize(24).doesNotHaveDuplicates().startsWith(mount(UP, NORTH), mount(UP, SOUTH),
                mount(UP, EAST), mount(UP, WEST), mount(DOWN, NORTH)).endsWith(mount(WEST, UP), mount(WEST, DOWN),
                mount(WEST, NORTH), mount(WEST, SOUTH));
        assertThat(Mount.all().subList(8, 12)).containsExactly(mount(NORTH, UP), mount(NORTH, DOWN), mount(NORTH, EAST),
                mount(NORTH, WEST));
    }

    @Test
    void aMountSitsOnAFlankNotOnTheSideItHoldsOnTo() {
        assertThatThrownBy(() -> new Mount(UP, UP)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Mount(UP, DOWN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPipeInTheAirHasNoMounts() {
        assertThat(Mounts.choose(NOTHING, NOTHING)).isEmpty();
        assertThat(Mounts.choose(Set.of(NORTH, SOUTH), NOTHING)).isEmpty();
    }

    @Test
    void aPipeOnTheFloorStandsOnTwoFeetNorthAndSouth() {
        assertThat(Mounts.choose(NOTHING, Set.of(DOWN))).containsExactly(mount(DOWN, NORTH), mount(DOWN, SOUTH));
    }

    @Test
    void theFeetMoveOutOfTheWayOfAnArm() {
        assertThat(Mounts.choose(Set.of(NORTH), Set.of(DOWN))).containsExactly(mount(DOWN, EAST), mount(DOWN, WEST));
        assertThat(Mounts.choose(Set.of(NORTH, SOUTH), Set.of(DOWN))).containsExactly(mount(DOWN, EAST), mount(DOWN, WEST));
        assertThat(Mounts.choose(Set.of(EAST), Set.of(DOWN))).containsExactly(mount(DOWN, NORTH), mount(DOWN, SOUTH));
    }

    @Test
    void whereOnlyTwoOrFewerFlanksAreFreeTheFeetTakeThose() {
        assertThat(Mounts.choose(Set.of(NORTH, EAST), Set.of(DOWN))).containsExactly(mount(DOWN, SOUTH), mount(DOWN, WEST));
        assertThat(Mounts.choose(Set.of(NORTH, EAST, SOUTH), Set.of(DOWN))).containsExactly(mount(DOWN, WEST));
        assertThat(Mounts.choose(Set.of(NORTH, EAST, SOUTH, WEST), Set.of(DOWN))).isEmpty();
    }

    @Test
    void theFloorWinsOverCeilingAndWalls() {
        assertThat(Mounts.choose(NOTHING, EVERYTHING)).containsExactly(mount(DOWN, NORTH), mount(DOWN, SOUTH));
    }

    @Test
    void withoutAFloorThePipeHangsFromTheCeiling() {
        assertThat(Mounts.choose(NOTHING, Set.of(UP, NORTH, EAST))).containsExactly(mount(UP, NORTH), mount(UP, SOUTH));
    }

    @Test
    void anArmGoingDownMakesThePipeHangFromTheCeilingEvenOverASolidBlock() {
        assertThat(Mounts.choose(Set.of(DOWN), Set.of(DOWN, UP))).containsExactly(mount(UP, NORTH), mount(UP, SOUTH));
    }

    @Test
    void aSingleWallHoldsThePipeLeftAndRight() {
        assertThat(Mounts.choose(NOTHING, Set.of(NORTH))).containsExactly(mount(NORTH, EAST), mount(NORTH, WEST));
        assertThat(Mounts.choose(NOTHING, Set.of(WEST))).containsExactly(mount(WEST, NORTH), mount(WEST, SOUTH));
    }

    @Test
    void aSingleWallHoldsThePipeAboveAndBelowWhenAnArmRunsAlongIt() {
        assertThat(Mounts.choose(Set.of(EAST), Set.of(NORTH))).containsExactly(mount(NORTH, UP), mount(NORTH, DOWN));
    }

    @Test
    void aSingleWallWithArmsOnTwoFlanksKeepsTheMountsThatAreLeft() {
        assertThat(Mounts.choose(Set.of(EAST, UP), Set.of(NORTH))).containsExactly(mount(NORTH, DOWN), mount(NORTH, WEST));
    }

    @Test
    void anArmGoingUpLeavesTheWallMountsLeftAndRight() {
        assertThat(Mounts.choose(Set.of(UP), Set.of(NORTH))).containsExactly(mount(NORTH, EAST), mount(NORTH, WEST));
    }

    @Test
    void oppositeWallsHoldThePipeWithOneMountEachOnTop() {
        assertThat(Mounts.choose(NOTHING, Set.of(NORTH, SOUTH))).containsExactly(mount(NORTH, UP), mount(SOUTH, UP));
        assertThat(Mounts.choose(NOTHING, Set.of(EAST, WEST))).containsExactly(mount(EAST, UP), mount(WEST, UP));
    }

    @Test
    void northAndSouthWallsComeBeforeEastAndWest() {
        assertThat(Mounts.choose(NOTHING, Set.of(NORTH, SOUTH, EAST, WEST))).containsExactly(mount(NORTH, UP), mount(SOUTH, UP));
        assertThat(Mounts.choose(NOTHING, Set.of(NORTH, EAST, WEST))).containsExactly(mount(EAST, UP), mount(WEST, UP));
    }

    @Test
    void oppositeWallsUseTheNextFreeFlankWhenAnArmGoesUp() {
        assertThat(Mounts.choose(Set.of(UP), Set.of(NORTH, SOUTH))).containsExactly(mount(NORTH, DOWN), mount(SOUTH, DOWN));
    }

    @Test
    void inACornerOfTwoWallsTheMountsSitOnTheOuterFlanks() {
        assertThat(Mounts.choose(NOTHING, Set.of(NORTH, EAST))).containsExactly(mount(NORTH, WEST), mount(EAST, SOUTH));
        assertThat(Mounts.choose(NOTHING, Set.of(EAST, SOUTH))).containsExactly(mount(SOUTH, WEST), mount(EAST, NORTH));
        assertThat(Mounts.choose(NOTHING, Set.of(SOUTH, WEST))).containsExactly(mount(SOUTH, EAST), mount(WEST, NORTH));
        assertThat(Mounts.choose(NOTHING, Set.of(WEST, NORTH))).containsExactly(mount(NORTH, EAST), mount(WEST, SOUTH));
    }

    @Test
    void inACornerWithAnArmOnTheOuterFlankTheFirstFreeFlankIsTaken() {
        assertThat(Mounts.choose(Set.of(WEST), Set.of(NORTH, EAST))).containsExactly(mount(NORTH, UP), mount(EAST, SOUTH));
    }

    @Test
    void aWallBehindAnArmHoldsNothing() {
        assertThat(Mounts.choose(Set.of(NORTH), Set.of(NORTH))).isEmpty();
    }

    // every way a pipe can be connected and surrounded
    @Test
    void neverMoreThanTwoMountsAndOnlyWhereTheyCanSit() {
        for (int arms = 0; arms < 64; arms++) {
            for (int blocks = 0; blocks < 64; blocks++) {
                Set<Side> connected = sides(arms);
                Set<Side> solid = sides(blocks);
                List<Mount> mounts = Mounts.choose(connected, solid);

                assertThat(mounts).as("arms %s, blocks %s", connected, solid).hasSizeLessThanOrEqualTo(2).doesNotHaveDuplicates()
                        .allSatisfy(mount -> {
                            assertThat(solid).contains(mount.towards());
                            assertThat(connected).doesNotContain(mount.towards(), mount.flank());
                        });
                if (mounts.size() == 2 && mounts.get(0).towards() != mounts.get(1).towards()) {
                    assertThat(mounts.get(0).towards()).isNotIn(UP, DOWN);
                    assertThat(mounts.get(1).towards()).isNotIn(UP, DOWN);
                }
            }
        }
    }

    private static Set<Side> named(String names) {
        Set<Side> sides = EnumSet.noneOf(Side.class);
        for (String name : names.split(",")) {
            if (!name.isEmpty()) {
                sides.add(Side.valueOf(name.toUpperCase(Locale.ROOT)));
            }
        }
        return sides;
    }

    // the table comes from scripts/check_pipe_mounts.py, where the rules of the original are written down a second time
    @Test
    void choosesTheSameAsTheRulesOfTheOriginalForEverySurrounding() throws IOException {
        List<String> lines;
        try (InputStream table = MountsTest.class.getResourceAsStream("/mounts.txt")) {
            lines = new String(table.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }

        assertThat(lines).hasSize(64 * 64);
        for (String line : lines) {
            String[] parts = line.split(";", -1);
            String chosen = Mounts.choose(named(parts[0]), named(parts[1])).stream()
                    .map(mount -> (mount.towards() + "_" + mount.flank()).toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(" "));

            assertThat(chosen).as("arms %s, blocks %s", parts[0], parts[1]).isEqualTo(parts[2]);
        }
    }

    @Test
    void aSolidBlockThatCanHoldThePipeAlwaysDoes() {
        for (int arms = 0; arms < 64; arms++) {
            for (Side block : Side.values()) {
                Set<Side> connected = sides(arms);
                boolean freeFlank = connected.stream().filter(side -> side != block && side != block.opposite()).count() < 4;

                assertThat(Mounts.choose(connected, Set.of(block)).isEmpty()).as("arms %s, block %s", connected, block)
                        .isEqualTo(connected.contains(block) || !freeFlank);
            }
        }
    }
}
