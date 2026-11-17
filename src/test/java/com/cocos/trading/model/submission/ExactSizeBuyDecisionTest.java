package com.cocos.trading.model.submission;

import static com.cocos.trading.model.submission.Submissions.PAMP_CLOSE;
import static com.cocos.trading.model.submission.Submissions.aUserWhoNeverTraded;
import static com.cocos.trading.model.submission.Submissions.marketBuyOf;
import static com.cocos.trading.model.submission.Submissions.pricedAt;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.ledger.Ledgers;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.order.RejectionReason;

class ExactSizeBuyDecisionTest {

	private static final AccountSnapshot SEEDED = Ledgers.seededUserOne().snapshot();

	@Test
	void the_comparison_is_against_available_cash_and_not_settled_cash() {
		assertThat(SEEDED.availableCash()).isEqualByComparingTo("627500.00");
		assertThat(SEEDED.settledCash()).isEqualByComparingTo("753000.00");
	}

	@Test
	void a_purchase_the_user_can_pay_for_is_filled_at_the_close() {
		OrderDecision decision = pricedAt(marketBuyOf(100), PAMP_CLOSE).decide(SEEDED);

		assertThat(decision.status()).isEqualTo(OrderStatus.FILLED);
		assertThat(decision.size()).isEqualByComparingTo("100");
		assertThat(decision.price().amount()).isEqualByComparingTo("925.85");
		assertThat(decision.rejectionReason()).isEmpty();
	}

	@Test
	void six_hundred_and_seventy_seven_shares_cost_626800_45_and_fit() {
		OrderDecision decision = pricedAt(marketBuyOf(677), PAMP_CLOSE).decide(SEEDED);

		assertThat(decision.status()).isEqualTo(OrderStatus.FILLED);
	}

	@Test
	void six_hundred_and_seventy_eight_shares_cost_627726_30_and_do_not() {
		OrderDecision decision = pricedAt(marketBuyOf(678), PAMP_CLOSE).decide(SEEDED);

		assertThat(decision.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(decision.rejectionReason()).contains(RejectionReason.INSUFFICIENT_FUNDS);
	}

	@Test
	void a_rejected_purchase_still_records_the_size_and_the_price_it_was_judged_at() {
		OrderDecision decision = pricedAt(marketBuyOf(1000), PAMP_CLOSE).decide(SEEDED);

		assertThat(decision.size()).isEqualByComparingTo("1000");
		assertThat(decision.price().amount()).isEqualByComparingTo("925.85");
	}

	@Test
	void a_user_with_no_orders_at_all_cannot_buy_one_share() {
		OrderDecision decision = pricedAt(marketBuyOf(1), PAMP_CLOSE).decide(aUserWhoNeverTraded());

		assertThat(decision.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(decision.rejectionReason()).contains(RejectionReason.INSUFFICIENT_FUNDS);
	}

}
