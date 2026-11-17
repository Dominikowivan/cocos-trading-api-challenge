package com.cocos.trading.model.submission;

import static com.cocos.trading.model.submission.Submissions.PAMP_CLOSE;
import static com.cocos.trading.model.submission.Submissions.aUserWhoNeverTraded;
import static com.cocos.trading.model.submission.Submissions.marketOrderOfAmount;
import static com.cocos.trading.model.submission.Submissions.pricedAt;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.ledger.Ledgers;
import com.cocos.trading.model.order.OrderSide;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.order.RejectionReason;

class AmountBuyDecisionTest {

	private static final AccountSnapshot SEEDED = Ledgers.seededUserOne().snapshot();

	private static OrderDecision buyOf(String amount) {
		return pricedAt(marketOrderOfAmount(OrderSide.BUY, amount), PAMP_CLOSE).decide(SEEDED);
	}

	@Test
	void an_amount_the_user_can_pay_derives_the_shares_that_fit_in_it() {
		OrderDecision decision = buyOf("100000.00");

		assertThat(decision.status()).isEqualTo(OrderStatus.FILLED);
		assertThat(decision.size()).isEqualByComparingTo("108");
		assertThat(decision.price().amount()).isEqualByComparingTo("925.85");
		assertThat(decision.rejectionReason()).isEmpty();
	}

	@Test
	void the_unspent_remainder_of_the_amount_is_neither_spent_nor_an_error() {
		OrderDecision decision = buyOf("100000.00");

		assertThat(decision.size().multiply(decision.price().amount())).isEqualByComparingTo("99991.80");
	}

	@Test
	void the_whole_available_cash_buys_six_hundred_and_seventy_seven_shares() {
		OrderDecision decision = buyOf("627500.00");

		assertThat(decision.status()).isEqualTo(OrderStatus.FILLED);
		assertThat(decision.size()).isEqualByComparingTo("677");
	}

	@Test
	void the_comparison_is_the_amount_itself_against_available_cash() {
		OrderDecision decision = buyOf("700000.00");

		assertThat(decision.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(decision.rejectionReason()).contains(RejectionReason.INSUFFICIENT_FUNDS);
	}

	@Test
	void an_amount_beyond_the_available_cash_never_gets_as_far_as_a_size() {
		assertThat(buyOf("700000.00").size()).isEqualByComparingTo("0");
	}

	@Test
	void money_that_does_not_reach_one_share_is_a_rejection_of_its_own() {
		OrderDecision decision = buyOf("500.00");

		assertThat(decision.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(decision.rejectionReason()).contains(RejectionReason.SIZE_BELOW_ONE_SHARE);
		assertThat(decision.size()).isEqualByComparingTo("0");
	}

	@Test
	void a_user_with_no_cash_is_short_of_funds_rather_than_short_of_a_share() {
		OrderDecision decision = pricedAt(marketOrderOfAmount(OrderSide.BUY, "500.00"), PAMP_CLOSE)
			.decide(aUserWhoNeverTraded());

		assertThat(decision.rejectionReason()).contains(RejectionReason.INSUFFICIENT_FUNDS);
	}

}
