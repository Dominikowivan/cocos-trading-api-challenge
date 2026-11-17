package com.cocos.trading.model.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import com.cocos.trading.model.Price;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TradeQuantityAmountTest {

	private static final Price PAMP_CLOSE = Price.of(new BigDecimal("925.85"));

	private static final BigDecimal A_CENT = new BigDecimal("0.01");

	private static final Price A_CENT_A_SHARE = Price.of(A_CENT);

	@Test
	void an_amount_needs_itself_whatever_the_price() {
		TradeQuantity amount = TradeQuantity.ofAmount(new BigDecimal("100000.00"));

		assertThat(amount.valueAt(PAMP_CLOSE)).isEqualByComparingTo("100000.00");
		assertThat(amount.valueAt(Price.of(new BigDecimal("1.00")))).isEqualByComparingTo("100000.00");
	}

	@Test
	void the_size_an_amount_makes_is_the_whole_shares_that_fit_in_it() {
		assertThat(TradeQuantity.ofAmount(new BigDecimal("100000.00")).sizeAt(PAMP_CLOSE)).isEqualByComparingTo("108");
	}

	@Test
	void the_price_decides_how_many_shares_the_same_amount_makes() {
		TradeQuantity amount = TradeQuantity.ofAmount(new BigDecimal("100000.00"));

		assertThat(amount.sizeAt(Price.of(new BigDecimal("900.00")))).isEqualByComparingTo("111");
	}

	@Test
	void an_amount_below_one_share_makes_no_size_rather_than_being_refused() {
		assertThat(TradeQuantity.ofAmount(new BigDecimal("500.00")).sizeAt(PAMP_CLOSE)).isEqualByComparingTo("0");
	}

	@Test
	void an_amount_states_no_size_of_its_own_before_a_price_derives_one() {
		assertThat(TradeQuantity.ofAmount(new BigDecimal("100000.00")).requestedSize()).isEqualByComparingTo("0");
	}

	@Test
	void a_cent_is_the_smallest_amount_there_is() {
		assertThat(TradeQuantity.ofAmount(A_CENT).valueAt(PAMP_CLOSE)).isEqualByComparingTo("0.01");
	}

	@Test
	void the_largest_amount_is_what_the_ledgers_price_column_holds() {
		assertThat(TradeQuantity.ofAmount(new BigDecimal("99999999.99")).valueAt(PAMP_CLOSE))
			.isEqualByComparingTo("99999999.99");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "0.001", "-0.01", "-1000", "100000000.00" })
	void an_amount_outside_the_ledgers_bounds_is_refused(String amount) {
		assertThatIllegalArgumentException().isThrownBy(() -> TradeQuantity.ofAmount(new BigDecimal(amount)))
			.withMessageContaining("total amount is between");
	}

	@Test
	void a_derived_size_the_ledger_cannot_hold_is_refused_like_a_requested_one() {
		TradeQuantity everything = TradeQuantity.ofAmount(new BigDecimal("99999999.99"));

		assertThatIllegalArgumentException().isThrownBy(() -> everything.sizeAt(A_CENT_A_SHARE))
			.withMessageContaining("an order can carry");
	}

	@Test
	void a_derived_size_at_the_ceiling_is_still_accepted() {
		TradeQuantity everything = TradeQuantity.ofAmount(new BigDecimal("21474836.47"));

		assertThat(everything.sizeAt(A_CENT_A_SHARE)).isEqualByComparingTo("2147483647");
	}

	@Test
	void an_order_states_a_size_or_an_amount_and_never_both() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> TradeQuantity.of(BigDecimal.TEN, new BigDecimal("1000.00")))
			.withMessageContaining("never both and never neither");
	}

	@Test
	void an_order_that_states_neither_cannot_be_read_as_either() {
		assertThatIllegalArgumentException().isThrownBy(() -> TradeQuantity.of(null, null))
			.withMessageContaining("never both and never neither");
	}

	@Test
	void an_order_that_states_one_of_the_two_is_read_as_that_one() {
		assertThat(TradeQuantity.of(BigDecimal.TEN, null).sizeAt(PAMP_CLOSE)).isEqualByComparingTo("10");
		assertThat(TradeQuantity.of(null, new BigDecimal("100000.00")).sizeAt(PAMP_CLOSE)).isEqualByComparingTo("108");
	}

}
