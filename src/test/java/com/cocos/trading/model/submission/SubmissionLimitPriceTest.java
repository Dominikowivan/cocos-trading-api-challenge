package com.cocos.trading.model.submission;

import static com.cocos.trading.model.ledger.Ledgers.PGR;
import static com.cocos.trading.model.submission.Submissions.aMarketNobodyMayConsult;
import static com.cocos.trading.model.submission.Submissions.anInstrumentThatDoesNotQuote;
import static com.cocos.trading.model.submission.Submissions.limitOrderOf;
import static com.cocos.trading.model.submission.Submissions.priceGoverning;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.order.OrderSide;

class SubmissionLimitPriceTest {

	@Test
	void the_price_in_the_request_governs() {
		assertThat(priceGoverning(limitOrderOf(OrderSide.BUY, 100, "900.00"), aMarketNobodyMayConsult()))
			.hasValueSatisfying(price -> assertThat(price.amount()).isEqualByComparingTo("900.00"));
	}

	@Test
	void the_market_is_never_consulted_even_when_it_quotes_the_instrument() {
		assertThat(priceGoverning(limitOrderOf(OrderSide.SELL, 40, "1000.00"), aMarketNobodyMayConsult()))
			.hasValueSatisfying(price -> assertThat(price.amount()).isEqualByComparingTo("1000.00"));
	}

	@Test
	void an_instrument_with_no_market_data_is_priced_like_any_other() {
		assertThat(priceGoverning(limitOrderOf(PGR, OrderSide.BUY, 1, "100.00"), anInstrumentThatDoesNotQuote()))
			.hasValueSatisfying(price -> assertThat(price.amount()).isEqualByComparingTo("100.00"));
	}

	@Test
	void a_price_nobody_will_trade_at_is_still_the_price_that_governs() {
		assertThat(priceGoverning(limitOrderOf(OrderSide.BUY, 100, "2000.00"), aMarketNobodyMayConsult()))
			.hasValueSatisfying(price -> assertThat(price.amount()).isEqualByComparingTo("2000.00"));
	}

	@Test
	void the_price_is_recorded_at_the_ledgers_scale() {
		assertThat(priceGoverning(limitOrderOf(OrderSide.BUY, 1, "900"), aMarketNobodyMayConsult()))
			.hasValueSatisfying(price -> assertThat(price.amount().scale()).isEqualTo(2));
	}

}
