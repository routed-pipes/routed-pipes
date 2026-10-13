package routedpipes.domain.sign;

import static org.assertj.core.api.Assertions.assertThat;

import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.Side;
import org.junit.jupiter.api.Test;

class SignFacesTest {

    private final SignFaces faces = new SignFaces();

    @Test
    void aSignIsFoundOnTheFaceItWasPlacedOn() {
        assertThat(faces.place(Side.NORTH, SignKind.CRAFTING, PipeKind.CRAFTING)).isTrue();

        assertThat(faces.at(Side.NORTH)).contains(SignKind.CRAFTING);
        assertThat(faces.at(Side.SOUTH)).isEmpty();
        assertThat(faces.has(SignKind.CRAFTING)).isTrue();
        assertThat(faces.has(SignKind.ITEM_AMOUNT)).isFalse();
    }

    @Test
    void aFaceHoldsOnlyOneSign() {
        faces.place(Side.NORTH, SignKind.CRAFTING, PipeKind.CRAFTING);

        assertThat(faces.place(Side.NORTH, SignKind.ITEM_AMOUNT, PipeKind.CRAFTING)).isFalse();
        assertThat(faces.at(Side.NORTH)).contains(SignKind.CRAFTING);
    }

    @Test
    void everyFaceCanHaveOne() {
        for (Side side : Side.values()) {
            assertThat(faces.place(side, SignKind.ITEM_AMOUNT, PipeKind.BASIC)).isTrue();
        }
        assertThat(faces.all()).hasSize(6);
    }

    @Test
    void aSignThePipeIsNotForIsRefused() {
        assertThat(faces.place(Side.UP, SignKind.CRAFTING, PipeKind.PROVIDER)).isFalse();
        assertThat(faces.place(Side.UP, SignKind.ITEM_AMOUNT, null)).isFalse();
        assertThat(faces.isEmpty()).isTrue();
    }

    @Test
    void removingTellsWhatWasThere() {
        faces.place(Side.UP, SignKind.ITEM_AMOUNT, PipeKind.BASIC);

        assertThat(faces.remove(Side.UP)).contains(SignKind.ITEM_AMOUNT);
        assertThat(faces.remove(Side.UP)).isEmpty();
        assertThat(faces.isEmpty()).isTrue();
    }

    @Test
    void oneSignOrTwoOnOppositeFacesStayClose() {
        faces.place(Side.NORTH, SignKind.ITEM_AMOUNT, PipeKind.BASIC);
        assertThat(faces.pushedOut(Side.NORTH)).isFalse();

        faces.place(Side.SOUTH, SignKind.ITEM_AMOUNT, PipeKind.BASIC);
        assertThat(faces.pushedOut(Side.NORTH)).isFalse();
        assertThat(faces.pushedOut(Side.SOUTH)).isFalse();
    }

    @Test
    void signsAroundACornerMoveOutwards() {
        faces.place(Side.NORTH, SignKind.ITEM_AMOUNT, PipeKind.BASIC);
        faces.place(Side.EAST, SignKind.ITEM_AMOUNT, PipeKind.BASIC);

        assertThat(faces.pushedOut(Side.NORTH)).isTrue();
        assertThat(faces.pushedOut(Side.EAST)).isTrue();
        assertThat(faces.pushedOut(Side.WEST)).isFalse();
    }

    @Test
    void threeAroundThePipeAllMoveOutwards() {
        faces.place(Side.NORTH, SignKind.ITEM_AMOUNT, PipeKind.BASIC);
        faces.place(Side.SOUTH, SignKind.ITEM_AMOUNT, PipeKind.BASIC);
        faces.place(Side.EAST, SignKind.ITEM_AMOUNT, PipeKind.BASIC);

        assertThat(faces.pushedOut(Side.NORTH)).isTrue();
        assertThat(faces.pushedOut(Side.SOUTH)).isTrue();
        assertThat(faces.pushedOut(Side.EAST)).isTrue();
    }

    @Test
    void signsOnTopAndBottomNeverMoveAndPushNothing() {
        faces.place(Side.UP, SignKind.ITEM_AMOUNT, PipeKind.BASIC);
        faces.place(Side.DOWN, SignKind.ITEM_AMOUNT, PipeKind.BASIC);
        faces.place(Side.NORTH, SignKind.ITEM_AMOUNT, PipeKind.BASIC);

        assertThat(faces.pushedOut(Side.UP)).isFalse();
        assertThat(faces.pushedOut(Side.DOWN)).isFalse();
        assertThat(faces.pushedOut(Side.NORTH)).isFalse();
    }
}
