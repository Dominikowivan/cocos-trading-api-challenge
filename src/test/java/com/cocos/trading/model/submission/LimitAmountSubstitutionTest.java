package com.cocos.trading.model.submission;

import static com.cocos.trading.model.submission.Submissions.PAMP_CLOSE;
import static com.cocos.trading.model.submission.Submissions.limitOrderOfAmount;
import static com.cocos.trading.model.submission.Submissions.marketOrderOfAmount;
import static com.cocos.trading.model.submission.Submissions.pricedAt;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.ledger.Ledgers;
import com.cocos.trading.model.order.OrderSide;
import com.cocos.trading.model.order.OrderStatus;

class LimitAmountSubstitutionTest {

	private static final AccountSnapshot SEEDED = Ledgers.seededUserOne().snapshot();

	@Test
	void a_hundred_thousand_pesos_buys_more_shares_at_a_price_below_the_close() {
		assertThat(limitBuyOf("100000.00", "900.00").size()).isEqualByComparingTo("111");
	}

	@Test
	void the_same_amount_at_market_buys_what_the_close_allows() {
		assertThat(pricedAt(marketOrderOfAmount(OrderSide.BUY, "100000.00"), PAMP_CLOSE).decide(SEEDED).size())
			.isEqualByComparingTo("108");
	}

	@Test
	void a_sale_for_an_amount_is_bounded_by_the_requested_price_on_both_sides() {
		assertThat(limitSellOf("30000.00", "1000.00").size()).isEqualByComparingTo("30");
	}

	@Test
	void the_same_sale_at_market_derives_the_shares_the_close_implies() {
		assertThat(pricedAt(marketOrderOfAmount(OrderSide.SELL, "30000.00"), PAMP_CLOSE).decide(SEEDED).size())
			.isEqualByComparingTo("32");
	}

	@Test
	void the_price_substitutes_in_what_is_available_and_not_only_in_the_derivation() {
		assertThat(limitSellOf("30000.00", "1000.00").status()).isEqualTo(OrderStatus.NEW);
		assertThat(limitSellOf("30000.00", "500.00").status()).isEqualTo(OrderStatus.REJECTED);
	}

	private static OrderDecision limitBuyOf(String amount, String price) {
		return decide(OrderSide.BUY, amount, price);
	}

	private static OrderDecision limitSellOf(String amount, String price) {
		return decide(OrderSide.SELL, amount, price);
	}

	private static OrderDecision decide(OrderSide side, String amount, String price) {
		return pricedAt(limitOrderOfAmount(side, amount, price), new BigDecimal(price)).decide(SEEDED);
	}

}
