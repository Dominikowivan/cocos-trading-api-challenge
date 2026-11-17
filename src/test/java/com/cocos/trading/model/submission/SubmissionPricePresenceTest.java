package com.cocos.trading.model.submission;

import static com.cocos.trading.model.ledger.Ledgers.PAMP;
import static com.cocos.trading.model.submission.Submissions.sizeOf;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.cocos.trading.model.order.OrderKind;
import com.cocos.trading.model.order.OrderSide;

class SubmissionPricePresenceTest {

	@Test
	void a_limit_order_with_no_price_has_nothing_to_be_validated_against() {
		assertThatIllegalArgumentException().isThrownBy(() -> limitOrderPricedAt(null))
			.withMessageContaining("LIMIT order requires a price");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "0.00", "-0.01", "-900.00" })
	void a_limit_price_at_or_below_zero_is_refused(String price) {
		assertThatIllegalArgumentException().isThrownBy(() -> limitOrderPricedAt(new BigDecimal(price)))
			.withMessageContaining("LIMIT order requires a price");
	}

	@Test
	void a_limit_price_beyond_what_the_ledger_holds_is_refused() {
		assertThatIllegalArgumentException().isThrownBy(() -> limitOrderPricedAt(new BigDecimal("100000000.00")))
			.withMessageContaining("LIMIT order requires a price");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0.01", "900.00", "99999999.99" })
	void a_limit_price_within_the_ledgers_bounds_is_accepted(String price) {
		assertThatNoException().isThrownBy(() -> limitOrderPricedAt(new BigDecimal(price)));
	}

	@Test
	void a_market_order_carrying_a_price_is_still_refused_rather_than_having_it_ignored() {
		assertThatIllegalArgumentException()
			.isThrownBy(
					() -> Submission.of(PAMP, OrderSide.BUY, OrderKind.MARKET, sizeOf(10), new BigDecimal("900.00")))
			.withMessageContaining("takes no price");
	}

	@Test
	void a_market_order_without_a_price_is_the_normal_case() {
		assertThatNoException()
			.isThrownBy(() -> Submission.of(PAMP, OrderSide.BUY, OrderKind.MARKET, sizeOf(10), null));
	}

	private static Submission limitOrderPricedAt(BigDecimal price) {
		return Submission.of(PAMP, OrderSide.BUY, OrderKind.LIMIT, sizeOf(10), price);
	}

}
