package com.cocos.trading.model.portfolio;

import static com.cocos.trading.model.ledger.Ledgers.BMA;
import static com.cocos.trading.model.ledger.Ledgers.METR;
import static com.cocos.trading.model.ledger.Ledgers.PAMP;
import static com.cocos.trading.model.ledger.Ledgers.PGR;
import static com.cocos.trading.model.ledger.Ledgers.filledBuy;
import static com.cocos.trading.model.ledger.Ledgers.filledSell;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.ledger.AccountLedger;
import com.cocos.trading.model.ledger.Ledgers;
import com.cocos.trading.model.order.Order;

class ValuationTotalReturnTest {

	@Test
	void the_return_is_the_gain_over_the_users_own_cost_in_percent_units() {
		Position position = only(valued(closes(PAMP, "925.85"),
				filledBuy(PAMP, 50, "930", "2023-07-12T12:31:20"),
				filledSell(PAMP, 10, "940", "2023-07-12T14:51:20")));

		assertThat(position.totalReturnPercent().orElseThrow()).isEqualByComparingTo("-0.45");
		assertThat(position.totalReturnPercent().orElseThrow().scale()).isEqualTo(2);
	}

	@Test
	void the_return_is_computed_from_the_close_it_is_given() {
		Position position = only(valued(closes(METR, "229.50"),
				filledBuy(METR, 500, "250", "2023-07-13T14:11:20")));

		assertThat(position.totalReturnPercent().orElseThrow()).isEqualByComparingTo("-8.20");
	}

	@Test
	void a_short_holding_reports_no_return() {
		Position position = only(valued(closes(BMA, "1502.80"),
				filledBuy(BMA, 20, "1540", "2023-07-13T12:51:20"),
				filledSell(BMA, 30, "1530", "2023-07-13T15:13:20")));

		assertThat(position.quantity()).isEqualByComparingTo("-10");
		assertThat(position.marketValue().orElseThrow()).isEqualByComparingTo("-15028.00");
		assertThat(position.totalReturnPercent()).isEmpty();
	}

	@Test
	void an_unpriced_holding_reports_no_return() {
		Position position = only(valued(Map.of(),
				filledBuy(PGR, 25, "900", "2023-07-14T11:00:00")));

		assertThat(position.marketValue()).isEmpty();
		assertThat(position.totalReturnPercent()).isEmpty();
	}

	@Test
	void a_holding_with_no_cost_reports_no_return() {
		Position position = only(valued(closes(PAMP, "925.85"),
				filledBuy(PAMP, 10, "0", "2023-07-12T12:31:20")));

		assertThat(position.marketValue().orElseThrow()).isEqualByComparingTo("9258.50");
		assertThat(position.totalReturnPercent()).isEmpty();
	}

	private static Map<Long, BigDecimal> closes(InstrumentRef instrument, String close) {
		return Map.of(instrument.instrumentId(), new BigDecimal(close));
	}

	private static Position only(Portfolio portfolio) {
		assertThat(portfolio.positions()).hasSize(1);
		return portfolio.positions().getFirst();
	}

	private static Portfolio valued(Map<Long, BigDecimal> closes, Order... orders) {
		return Portfolio.valued(new AccountLedger(Ledgers.orders(orders)).snapshot(), new Closes(closes));
	}

}
