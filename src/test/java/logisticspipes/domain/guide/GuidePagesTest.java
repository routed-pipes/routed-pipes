package logisticspipes.domain.guide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import logisticspipes.domain.guide.GuidePages.Piece;
import org.junit.jupiter.api.Test;

class GuidePagesTest {

    @Test
    void entriesThatFitShareAPageWithABlankLineBetween() {
        assertThat(GuidePages.layout(List.of(3, 4), 8)).containsExactly(List.of(new Piece(0, 0, 3), new Piece(1, 0, 4)));
    }

    @Test
    void theBlankLineCountsToo() {
        assertThat(GuidePages.layout(List.of(4, 4), 8)).containsExactly(List.of(new Piece(0, 0, 4)), List.of(new Piece(1, 0, 4)));
    }

    @Test
    void anEntryThatDoesNotFitStartsOnTheNextPage() {
        assertThat(GuidePages.layout(List.of(5, 5, 2), 8))
                .containsExactly(List.of(new Piece(0, 0, 5)), List.of(new Piece(1, 0, 5), new Piece(2, 0, 2)));
    }

    @Test
    void anEntryLongerThanAPageGoesOnOverSeveralPages() {
        assertThat(GuidePages.layout(List.of(2, 12, 1), 5)).containsExactly(
                List.of(new Piece(0, 0, 2), new Piece(1, 0, 2)),
                List.of(new Piece(1, 2, 7)),
                List.of(new Piece(1, 7, 12)),
                List.of(new Piece(2, 0, 1)));
    }

    @Test
    void aLongEntryAtTheEndOfAFullPageStartsOnTheNextOne() {
        assertThat(GuidePages.layout(List.of(4, 9), 5)).containsExactly(
                List.of(new Piece(0, 0, 4)),
                List.of(new Piece(1, 0, 5)),
                List.of(new Piece(1, 5, 9)));
    }

    @Test
    void nothingMakesNoPages() {
        assertThat(GuidePages.layout(List.of(), 5)).isEmpty();
    }

    @Test
    void aPageNeedsALine() {
        assertThatThrownBy(() -> GuidePages.layout(List.of(1), 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
