package com.cocos.trading.model.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import com.cocos.trading.model.Price;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TradeQuantityExactSizeTest {

	private static final Price PAMP_CLOSE = Price.of(new BigDecimal("925.85"));

	@Test
	void a_size_needs_itself_times_the_price() {
		assertThat(TradeQuantity.ofSize(BigDecimal.valueOf(100)).valueAt(PAMP_CLOSE)).isEqualByComparingTo("92585.00");
	}

	@Test
	void an_exact_size_is_the_size_whatever_the_price() {
		TradeQuantity hundred = TradeQuantity.ofSize(BigDecimal.valueOf(100));

		assertThat(hundred.sizeAt(PAMP_CLOSE)).isEqualByComparingTo("100");
		assertThat(hundred.sizeAt(Price.of(BigDecimal.ONE))).isEqualByComparingTo("100");
	}

	@Test
	void one_share_is_the_smallest_order_there_is() {
		assertThat(TradeQuantity.ofSize(BigDecimal.ONE).sizeAt(PAMP_CLOSE)).isEqualByComparingTo("1");
	}

	@Test
	void the_largest_size_is_what_the_ledgers_column_holds() {
		assertThat(TradeQuantity.ofSize(BigDecimal.valueOf(Integer.MAX_VALUE)).sizeAt(PAMP_CLOSE))
			.isEqualByComparingTo("2147483647");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1", "-100", "2147483648", "9999999999" })
	void a_size_outside_the_ledgers_bounds_is_refused(String size) {
		assertThatIllegalArgumentException().isThrownBy(() -> TradeQuantity.ofSize(new BigDecimal(size)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "10.5", "0.5", "1.0001" })
	void a_fraction_of_a_share_is_refused_rather_than_rounded(String size) {
		assertThatIllegalArgumentException().isThrownBy(() -> TradeQuantity.ofSize(new BigDecimal(size)))
			.withMessageContaining("whole number of shares");
	}

	@Test
	void a_whole_size_written_with_decimals_is_still_whole() {
		assertThat(TradeQuantity.ofSize(new BigDecimal("100.00")).sizeAt(PAMP_CLOSE)).isEqualByComparingTo("100");
	}

}
