package com.cocos.trading.model.submission;

import static com.cocos.trading.model.submission.Submissions.limitOrderOfAmount;
import static com.cocos.trading.model.submission.Submissions.pricedAt;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.ledger.Ledgers;
import com.cocos.trading.model.order.OrderSide;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.order.RejectionReason;

class LimitAmountDecisionTest {

	private static final AccountSnapshot SEEDED = Ledgers.seededUserOne().snapshot();

	@Test
	void an_amount_beyond_the_available_cash_is_rejected_whatever_the_price() {
		OrderDecision decision = buyOf("700000.00", "900.00");

		assertThat(decision.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(decision.rejectionReason()).contains(RejectionReason.INSUFFICIENT_FUNDS);
		assertThat(decision.size()).isEqualByComparingTo("0");
	}

	@Test
	void an_amount_that_does_not_reach_one_share_at_the_requested_price_is_rejected() {
		OrderDecision decision = buyOf("800.00", "900.00");

		assertThat(decision.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(decision.rejectionReason()).contains(RejectionReason.SIZE_BELOW_ONE_SHARE);
		assertThat(decision.size()).isEqualByComparingTo("0");
	}

	@Test
	void an_accepted_limit_order_for_an_amount_is_born_new() {
		OrderDecision decision = buyOf("100000.00", "900.00");

		assertThat(decision.status()).isEqualTo(OrderStatus.NEW);
		assertThat(decision.size()).isEqualByComparingTo("111");
		assertThat(decision.price().amount()).isEqualByComparingTo("900.00");
	}

	@Test
	void a_position_valued_at_the_requested_price_is_what_a_sale_is_judged_against() {
		OrderDecision decision = sellOf("50000.00", "1000.00");

		assertThat(decision.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(decision.rejectionReason()).contains(RejectionReason.INSUFFICIENT_HOLDINGS);
	}

	@Test
	void the_same_sale_is_rejected_at_one_price_and_accepted_at_another() {
		assertThat(sellOf("30000.00", "500.00").status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(sellOf("30000.00", "500.00").rejectionReason()).contains(RejectionReason.INSUFFICIENT_HOLDINGS);
		assertThat(sellOf("30000.00", "1000.00").status()).isEqualTo(OrderStatus.NEW);
	}

	private static OrderDecision buyOf(String amount, String price) {
		return decide(OrderSide.BUY, amount, price);
	}

	private static OrderDecision sellOf(String amount, String price) {
		return decide(OrderSide.SELL, amount, price);
	}

	private static OrderDecision decide(OrderSide side, String amount, String price) {
		return pricedAt(limitOrderOfAmount(side, amount, price), new BigDecimal(price)).decide(SEEDED);
	}

}
