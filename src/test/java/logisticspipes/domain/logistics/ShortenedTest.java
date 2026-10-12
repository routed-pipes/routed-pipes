package logisticspipes.domain.logistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ShortenedTest {

    @Test
    void keepsAShortListAsItIs() {
        Shortened<String> list = Shortened.of(List.of("a", "b"), 3);

        assertThat(list.shown()).containsExactly("a", "b");
        assertThat(list.more()).isZero();
    }

    @Test
    void cutsALongListAndCountsTheRest() {
        Shortened<String> list = Shortened.of(List.of("a", "b", "c", "d", "e"), 3);

        assertThat(list.shown()).containsExactly("a", "b", "c");
        assertThat(list.more()).isEqualTo(2);
    }

    @Test
    void aListOfExactlyTheLimitLeavesNothingOut() {
        assertThat(Shortened.of(List.of("a", "b", "c"), 3).more()).isZero();
    }

    @Test
    void nothingListedIsEmpty() {
        assertThat(Shortened.of(List.of(), 3).isEmpty()).isTrue();
        assertThat(Shortened.of(List.of("a"), 3).isEmpty()).isFalse();
    }
}
