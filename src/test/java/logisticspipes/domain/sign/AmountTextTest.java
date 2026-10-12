package logisticspipes.domain.sign;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AmountTextTest {

    @Test
    void smallAmountsAreWrittenOut() {
        assertThat(AmountText.of(0)).isEqualTo("0");
        assertThat(AmountText.of(64)).isEqualTo("64");
        assertThat(AmountText.of(999)).isEqualTo("999");
    }

    @Test
    void thousandsGetAUnitAndKeepThreeDigits() {
        assertThat(AmountText.of(1000)).isEqualTo("1K");
        assertThat(AmountText.of(1234)).isEqualTo("1.23K");
        assertThat(AmountText.of(1500)).isEqualTo("1.5K");
        assertThat(AmountText.of(12345)).isEqualTo("12.3K");
        assertThat(AmountText.of(123456)).isEqualTo("123K");
    }

    @Test
    void zerosBehindThePointAreKeptWhereTheyCount() {
        assertThat(AmountText.of(1010)).isEqualTo("1.01K");
        assertThat(AmountText.of(1005)).isEqualTo("1K");
        assertThat(AmountText.of(12045)).isEqualTo("12K");
    }

    @Test
    void theAmountIsCutOffAndNeverRoundedUp() {
        assertThat(AmountText.of(1999)).isEqualTo("1.99K");
        assertThat(AmountText.of(999999)).isEqualTo("999K");
    }

    @Test
    void millionsAndBeyond() {
        assertThat(AmountText.of(1000000)).isEqualTo("1M");
        assertThat(AmountText.of(1234567)).isEqualTo("1.23M");
        assertThat(AmountText.of(Integer.MAX_VALUE)).isEqualTo("2.14G");
    }

    @Test
    void nothingIsShownAsLessThanNothing() {
        assertThat(AmountText.of(-5)).isEqualTo("0");
    }
}
