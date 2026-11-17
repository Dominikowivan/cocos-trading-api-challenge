package com.cocos.trading.model.submission;

import static com.cocos.trading.model.submission.Submissions.PAMP_CLOSE;
import static com.cocos.trading.model.submission.Submissions.limitOrderOf;
import static com.cocos.trading.model.submission.Submissions.marketBuyOf;
import static com.cocos.trading.model.submission.Submissions.pricedAt;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.ledger.Ledgers;
import com.cocos.trading.model.order.OrderSide;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.order.RejectionReason;

class LimitExactSizeDecisionTest {

	private static final AccountSnapshot SEEDED = Ledgers.seededUserOne().snapshot();

	private static OrderDecision buyOf(long size, String price) {
		return pricedAt(limitOrderOf(OrderSide.BUY, size, price), new BigDecimal(price)).decide(SEEDED);
	}

	@Test
	void an_accepted_limit_order_is_born_new_and_does_not_fill() {
		OrderDecision decision = buyOf(100, "900.00");

		assertThat(decision.status()).isEqualTo(OrderStatus.NEW);
		assertThat(decision.size()).isEqualByComparingTo("100");
		assertThat(decision.price().amount()).isEqualByComparingTo("900.00");
		assertThat(decision.rejectionReason()).isEmpty();
	}

	@Test
	void seven_hundred_shares_at_nine_hundred_cost_more_than_is_available() {
		OrderDecision decision = buyOf(700, "900.00");

		assertThat(decision.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(decision.rejectionReason()).contains(RejectionReason.INSUFFICIENT_FUNDS);
	}

	@Test
	void the_rejection_is_recorded_at_the_requested_price_and_not_at_the_close() {
		assertThat(buyOf(700, "900.00").price().amount()).isEqualByComparingTo("900.00");
	}

	@Test
	void a_size_the_close_cannot_fund_is_funded_at_a_lower_requested_price() {
		assertThat(buyOf(697, "900.00").status()).isEqualTo(OrderStatus.NEW);
		assertThat(pricedAt(marketBuyOf(697), PAMP_CLOSE).decide(SEEDED).status()).isEqualTo(OrderStatus.REJECTED);
	}

	@Test
	void a_price_nobody_will_trade_at_is_not_a_rejection_reason() {
		OrderDecision decision = buyOf(100, "2000.00");

		assertThat(decision.status()).isEqualTo(OrderStatus.NEW);
		assertThat(decision.price().amount()).isEqualByComparingTo("2000.00");
	}

	@Test
	void a_sale_of_the_whole_position_is_accepted_and_one_share_more_is_not() {
		assertThat(sellOf(40, "1000.00").status()).isEqualTo(OrderStatus.NEW);

		OrderDecision rejected = sellOf(41, "1000.00");

		assertThat(rejected.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(rejected.rejectionReason()).contains(RejectionReason.INSUFFICIENT_HOLDINGS);
	}

	private static OrderDecision sellOf(long size, String price) {
		return pricedAt(limitOrderOf(OrderSide.SELL, size, price), new BigDecimal(price)).decide(SEEDED);
	}

}
