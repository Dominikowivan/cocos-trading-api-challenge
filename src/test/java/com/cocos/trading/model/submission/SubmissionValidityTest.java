package com.cocos.trading.model.submission;

import static com.cocos.trading.model.ledger.Ledgers.ARS;
import static com.cocos.trading.model.ledger.Ledgers.PAMP;
import static com.cocos.trading.model.submission.Submissions.sizeOf;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.cocos.trading.model.order.OrderKind;
import com.cocos.trading.model.order.OrderSide;

class SubmissionValidityTest {

	@Test
	void cash_is_not_a_tradable_instrument() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> Submission.of(ARS, OrderSide.BUY, OrderKind.MARKET, sizeOf(10), null))
			.withMessageContaining("is cash");
	}

	@Test
	void a_market_order_carrying_a_price_is_refused_and_never_ignored() {
		assertThatIllegalArgumentException()
			.isThrownBy(
					() -> Submission.of(PAMP, OrderSide.BUY, OrderKind.MARKET, sizeOf(10), new BigDecimal("900.00")))
			.withMessageContaining("takes no price");
	}

	@Test
	void a_limit_order_without_a_price_is_refused() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> Submission.of(PAMP, OrderSide.BUY, OrderKind.LIMIT, sizeOf(10), null))
			.withMessageContaining("LIMIT order requires a price");
	}

	@ParameterizedTest
	@EnumSource(OrderSide.class)
	void a_market_order_for_a_tradable_instrument_is_valid(OrderSide side) {
		assertThatNoException().isThrownBy(() -> Submission.of(PAMP, side, OrderKind.MARKET, sizeOf(10), null));
	}

}
