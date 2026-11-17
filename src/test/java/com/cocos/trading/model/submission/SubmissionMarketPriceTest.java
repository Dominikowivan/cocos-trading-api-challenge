package com.cocos.trading.model.submission;

import static com.cocos.trading.model.ledger.Ledgers.PAMP;
import static com.cocos.trading.model.ledger.Ledgers.PGR;
import static com.cocos.trading.model.submission.Submissions.anInstrumentThatDoesNotQuote;
import static com.cocos.trading.model.submission.Submissions.marketBuyOf;
import static com.cocos.trading.model.submission.Submissions.marketOrderOf;
import static com.cocos.trading.model.submission.Submissions.priceGoverning;
import static com.cocos.trading.model.submission.Submissions.theMarketOn;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.Price;
import com.cocos.trading.model.instrument.Closes;
import com.cocos.trading.model.order.OrderSide;

class SubmissionMarketPriceTest {

	@Test
	void the_latest_close_of_the_instrument_governs() {
		Optional<Price> price = priceGoverning(marketBuyOf(100), theMarketOn(PAMP, "925.85"));

		assertThat(price).hasValueSatisfying(close -> assertThat(close.amount()).isEqualByComparingTo("925.85"));
	}

	@Test
	void the_side_does_not_change_which_price_governs() {
		Supplier<Closes> market = theMarketOn(PAMP, "925.85");

		assertThat(priceGoverning(marketOrderOf(OrderSide.SELL, 100), market))
			.isEqualTo(priceGoverning(marketOrderOf(OrderSide.BUY, 100), market));
	}

	@Test
	void another_instruments_close_does_not_price_this_one() {
		Optional<Price> price = priceGoverning(marketOrderOf(PGR, OrderSide.BUY, 1), theMarketOn(PAMP, "925.85"));

		assertThat(price).isEmpty();
	}

	@Test
	void an_instrument_with_no_close_has_no_price_to_execute_at() {
		Optional<Price> price = priceGoverning(marketOrderOf(PGR, OrderSide.BUY, 1), anInstrumentThatDoesNotQuote());

		assertThat(price).isEmpty();
	}

	@Test
	void a_close_that_is_not_a_price_quotes_nothing_at_all() {
		assertThat(priceGoverning(marketBuyOf(100), theMarketOn(PAMP, "0.00"))).isEmpty();
		assertThat(priceGoverning(marketBuyOf(100), theMarketOn(PAMP, "-925.85"))).isEmpty();
	}

	@Test
	void a_close_beyond_what_the_ledger_holds_quotes_nothing_either() {
		assertThat(priceGoverning(marketBuyOf(100), theMarketOn(PAMP, "100000000.00"))).isEmpty();
	}

}
