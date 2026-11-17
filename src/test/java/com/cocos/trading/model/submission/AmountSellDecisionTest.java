package com.cocos.trading.model.submission;

import static com.cocos.trading.model.submission.Submissions.PAMP_CLOSE;
import static com.cocos.trading.model.submission.Submissions.marketOrderOfAmount;
import static com.cocos.trading.model.submission.Submissions.pricedAt;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.ledger.Ledgers;
import com.cocos.trading.model.order.OrderSide;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.order.RejectionReason;

class AmountSellDecisionTest {

	private static final AccountSnapshot SEEDED = Ledgers.seededUserOne().snapshot();

	private static OrderDecision sellOf(String amount) {
		return pricedAt(marketOrderOfAmount(OrderSide.SELL, amount), PAMP_CLOSE).decide(SEEDED);
	}

	@Test
	void the_position_is_valued_at_the_governing_price_and_compared_against_the_amount() {
		assertThat(SEEDED.availableQuantityOf(Ledgers.PAMP.instrumentId())).isEqualByComparingTo("40");

		OrderDecision decision = sellOf("30000.00");

		assertThat(decision.status()).isEqualTo(OrderStatus.FILLED);
		assertThat(decision.size()).isEqualByComparingTo("32");
	}

	@Test
	void the_whole_position_is_sellable_to_the_cent() {
		OrderDecision decision = sellOf("37034.00");

		assertThat(decision.status()).isEqualTo(OrderStatus.FILLED);
		assertThat(decision.size()).isEqualByComparingTo("40");
	}

	@Test
	void an_amount_worth_more_than_the_position_is_rejected() {
		OrderDecision decision = sellOf("40000.00");

		assertThat(decision.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(decision.rejectionReason()).contains(RejectionReason.INSUFFICIENT_HOLDINGS);
		assertThat(decision.size()).isEqualByComparingTo("0");
	}

	@Test
	void an_amount_that_does_not_reach_one_share_is_rejected_for_the_share_and_not_the_holding() {
		OrderDecision decision = sellOf("900.00");

		assertThat(decision.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(decision.rejectionReason()).contains(RejectionReason.SIZE_BELOW_ONE_SHARE);
		assertThat(decision.size()).isEqualByComparingTo("0");
	}

	@Test
	void a_negative_position_sells_nothing_at_any_amount() {
		OrderDecision decision = pricedAt(marketOrderOfAmount(Ledgers.BMA, OrderSide.SELL, "100.00"),
				new BigDecimal("1500.00"))
			.decide(SEEDED);

		assertThat(SEEDED.availableQuantityOf(Ledgers.BMA.instrumentId())).isEqualByComparingTo("-10");
		assertThat(decision.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(decision.rejectionReason()).contains(RejectionReason.INSUFFICIENT_HOLDINGS);
	}

}
