package com.cocos.trading.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PriceTest {

	private static final Price PAMP_CLOSE = Price.of(new BigDecimal("925.85"));

	@ParameterizedTest
	@ValueSource(strings = { "0", "0.00", "0.004", "-0.01", "-925.85", "100000000.00" })
	void an_amount_that_the_ledger_cannot_trade_at_is_not_a_price(String amount) {
		assertThat(Price.usable(new BigDecimal(amount))).isEmpty();
	}

	@ParameterizedTest
	@NullSource
	void an_amount_that_was_never_published_is_not_a_price(BigDecimal amount) {
		assertThat(Price.usable(amount)).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "0.01", "925.85", "99999999.99" })
	void an_amount_within_the_ledgers_bounds_is_a_price(String amount) {
		assertThat(Price.usable(new BigDecimal(amount))).isPresent();
	}

	@Test
	void a_price_is_carried_at_the_ledgers_scale() {
		assertThat(Price.of(new BigDecimal("900")).amount().scale()).isEqualTo(2);
	}

	@Test
	void two_prices_of_the_same_amount_are_the_same_price() {
		assertThat(Price.of(new BigDecimal("900"))).isEqualTo(Price.of(new BigDecimal("900.00")));
	}

	@Test
	void demanding_a_price_of_what_is_not_one_says_what_a_price_is() {
		assertThatIllegalArgumentException().isThrownBy(() -> Price.of(BigDecimal.ZERO))
			.withMessageContaining("A price is between 0.01 and 99999999.99");
	}

	@Test
	void shares_are_worth_the_price_each() {
		assertThat(PAMP_CLOSE.times(BigDecimal.valueOf(100))).isEqualByComparingTo("92585.00");
	}

	@Test
	void money_buys_the_whole_shares_that_fit_in_it_and_no_fraction_of_one() {
		assertThat(PAMP_CLOSE.wholeSharesIn(new BigDecimal("100000.00"))).isEqualByComparingTo("108");
		assertThat(PAMP_CLOSE.wholeSharesIn(new BigDecimal("500.00"))).isEqualByComparingTo("0");
	}

	@Test
	void no_price_can_ever_divide_money_by_zero_because_zero_is_not_a_price() {
		assertThat(Price.usable(BigDecimal.ZERO)).isEmpty();
	}

}
