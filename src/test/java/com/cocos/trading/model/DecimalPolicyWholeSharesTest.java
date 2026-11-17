package com.cocos.trading.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class DecimalPolicyWholeSharesTest {

	private static final BigDecimal PAMP_CLOSE = new BigDecimal("925.85");

	@Test
	void a_hundred_thousand_pesos_of_pamp_is_a_hundred_and_eight_shares() {
		assertThat(DecimalPolicy.wholeSharesIn(new BigDecimal("100000.00"), PAMP_CLOSE)).isEqualByComparingTo("108");
	}

	@Test
	void the_answer_is_a_whole_number_of_shares_and_says_so_in_its_scale() {
		assertThat(DecimalPolicy.wholeSharesIn(new BigDecimal("100000.00"), PAMP_CLOSE).scale()).isZero();
	}

	@Test
	void the_remainder_is_dropped_and_never_rounded_up() {
		assertThat(DecimalPolicy.wholeSharesIn(new BigDecimal("1000.00"), new BigDecimal("525.00")))
			.isEqualByComparingTo("1");
	}

	@Test
	void an_amount_that_does_not_reach_one_share_fits_no_shares_at_all() {
		assertThat(DecimalPolicy.wholeSharesIn(new BigDecimal("500.00"), PAMP_CLOSE)).isEqualByComparingTo("0");
	}

	@Test
	void an_amount_that_is_an_exact_multiple_of_the_price_loses_nothing() {
		assertThat(DecimalPolicy.wholeSharesIn(new BigDecimal("37034.00"), PAMP_CLOSE)).isEqualByComparingTo("40");
	}

	@Test
	void the_whole_available_cash_of_the_seeded_user_buys_six_hundred_and_seventy_seven_shares() {
		assertThat(DecimalPolicy.wholeSharesIn(new BigDecimal("627500.00"), PAMP_CLOSE)).isEqualByComparingTo("677");
	}

}
