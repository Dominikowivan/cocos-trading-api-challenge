package com.cocos.trading.model.submission;

import static com.cocos.trading.model.ledger.Ledgers.BMA;
import static com.cocos.trading.model.submission.Submissions.PAMP_CLOSE;
import static com.cocos.trading.model.submission.Submissions.aUserWhoNeverTraded;
import static com.cocos.trading.model.submission.Submissions.marketOrderOf;
import static com.cocos.trading.model.submission.Submissions.marketSellOf;
import static com.cocos.trading.model.submission.Submissions.pricedAt;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.ledger.Ledgers;
import com.cocos.trading.model.order.OrderSide;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.order.RejectionReason;

class ExactSizeSellDecisionTest {

	private static final AccountSnapshot SEEDED = Ledgers.seededUserOne().snapshot();

	private static final BigDecimal BMA_CLOSE = new BigDecimal("1502.80");

	@Test
	void the_comparison_is_against_the_available_holding() {
		assertThat(SEEDED.availableQuantityOf(Ledgers.PAMP.instrumentId())).isEqualByComparingTo("40");
		assertThat(SEEDED.availableQuantityOf(BMA.instrumentId())).isEqualByComparingTo("-10");
	}

	@Test
	void selling_the_whole_position_is_filled() {
		OrderDecision decision = pricedAt(marketSellOf(40), PAMP_CLOSE).decide(SEEDED);

		assertThat(decision.status()).isEqualTo(OrderStatus.FILLED);
		assertThat(decision.size()).isEqualByComparingTo("40");
		assertThat(decision.price().amount()).isEqualByComparingTo("925.85");
		assertThat(decision.rejectionReason()).isEmpty();
	}

	@Test
	void one_share_more_than_the_position_is_rejected() {
		OrderDecision decision = pricedAt(marketSellOf(41), PAMP_CLOSE).decide(SEEDED);

		assertThat(decision.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(decision.rejectionReason()).contains(RejectionReason.INSUFFICIENT_HOLDINGS);
		assertThat(decision.size()).isEqualByComparingTo("41");
	}

	@ParameterizedTest
	@ValueSource(longs = { 1, 10, 1000 })
	void a_position_at_or_below_zero_refuses_every_sale(long size) {
		OrderDecision decision = pricedAt(marketOrderOf(BMA, OrderSide.SELL, size), BMA_CLOSE).decide(SEEDED);

		assertThat(decision.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(decision.rejectionReason()).contains(RejectionReason.INSUFFICIENT_HOLDINGS);
	}

	@Test
	void a_user_with_no_orders_at_all_holds_nothing_to_sell() {
		OrderDecision decision = pricedAt(marketSellOf(1), PAMP_CLOSE).decide(aUserWhoNeverTraded());

		assertThat(decision.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(decision.rejectionReason()).contains(RejectionReason.INSUFFICIENT_HOLDINGS);
	}

	@Test
	void cash_is_never_what_a_sale_is_judged_against() {
		OrderDecision decision = pricedAt(marketSellOf(100), PAMP_CLOSE).decide(SEEDED);

		assertThat(SEEDED.availableCash()).isGreaterThan(new BigDecimal("92585.00"));
		assertThat(decision.rejectionReason()).contains(RejectionReason.INSUFFICIENT_HOLDINGS);
	}

}
