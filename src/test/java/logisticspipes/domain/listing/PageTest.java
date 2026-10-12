package logisticspipes.domain.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class PageTest {

    private static final List<Integer> SEVEN = List.of(1, 2, 3, 4, 5, 6, 7);

    @Test
    void anEmptyListStillHasOnePage() {
        Page<Integer> page = Page.of(List.of(), 0, 3);

        assertThat(page.pages()).isEqualTo(1);
        assertThat(page.number()).isZero();
        assertThat(page.entries()).isEmpty();
    }

    @Test
    void cutsTheListIntoPages() {
        assertThat(Page.of(SEVEN, 0, 3).entries()).containsExactly(1, 2, 3);
        assertThat(Page.of(SEVEN, 1, 3).entries()).containsExactly(4, 5, 6);
        assertThat(Page.of(SEVEN, 0, 3).pages()).isEqualTo(3);
    }

    @Test
    void theLastPageHoldsWhatIsLeft() {
        assertThat(Page.of(SEVEN, 2, 3).entries()).containsExactly(7);
    }

    @Test
    void aListThatFillsItsPagesHasNoEmptyOneAtTheEnd() {
        assertThat(Page.of(List.of(1, 2, 3, 4, 5, 6), 0, 3).pages()).isEqualTo(2);
    }

    @Test
    void aPageBeyondTheEndIsTheLastOne() {
        Page<Integer> page = Page.of(SEVEN, 9, 3);

        assertThat(page.number()).isEqualTo(2);
        assertThat(page.entries()).containsExactly(7);
    }

    @Test
    void aPageBeforeTheStartIsTheFirstOne() {
        Page<Integer> page = Page.of(SEVEN, -1, 3);

        assertThat(page.number()).isZero();
        assertThat(page.entries()).containsExactly(1, 2, 3);
    }

    @Test
    void refusesPagesWithoutRoom() {
        assertThatThrownBy(() -> Page.of(SEVEN, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
