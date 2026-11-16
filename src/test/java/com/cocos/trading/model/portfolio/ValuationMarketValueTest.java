package com.cocos.trading.model.portfolio;

import static com.cocos.trading.model.ledger.Ledgers.BMA;
import static com.cocos.trading.model.ledger.Ledgers.PAMP;
import static com.cocos.trading.model.ledger.Ledgers.PGR;
import static com.cocos.trading.model.ledger.Ledgers.filledBuy;
import static com.cocos.trading.model.ledger.Ledgers.filledSell;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.ledger.AccountLedger;
import com.cocos.trading.model.ledger.Ledgers;
import com.cocos.trading.model.order.Order;

class ValuationMarketValueTest {

	@Test
	void market_value_is_quantity_times_the_latest_close_at_scale_two() {
		Portfolio portfolio = valued(Map.of(PAMP.instrumentId(), new BigDecimal("925.85")),
				filledBuy(PAMP, 50, "930", "2023-07-12T12:31:20"), filledSell(PAMP, 10, "940", "2023-07-12T14:51:20"));

		BigDecimal marketValue = portfolio.positions().getFirst().marketValue().orElseThrow();
		assertThat(marketValue).isEqualByComparingTo("37034.00");
		assertThat(marketValue.scale()).isEqualTo(2);
	}

	@Test
	void a_negative_quantity_yields_a_negative_market_value() {
		Portfolio portfolio = valued(Map.of(BMA.instrumentId(), new BigDecimal("1502.80")),
				filledBuy(BMA, 20, "1540", "2023-07-13T12:51:20"), filledSell(BMA, 30, "1530", "2023-07-13T15:13:20"));

		assertThat(portfolio.positions().getFirst().marketValue().orElseThrow()).isEqualByComparingTo("-15028.00");
	}

	@Test
	void an_unpriced_holding_reports_an_absent_market_value_and_is_the_one_unpriced_position() {
		Portfolio portfolio = valued(Map.of(), filledBuy(PGR, 25, "900", "2023-07-14T11:00:00"));

		assertThat(portfolio.positions().getFirst().marketValue()).isEmpty();
		assertThat(portfolio.unpricedPositions()).hasSize(1);
		assertThat(portfolio.unpricedPositions().getFirst().instrument().ticker()).isEqualTo("PGR");
		assertThat(portfolio.shortPositions()).isEmpty();
	}

	@Test
	void a_priced_holding_is_not_an_unpriced_position() {
		Portfolio portfolio = valued(Map.of(PAMP.instrumentId(), new BigDecimal("925.85")),
				filledBuy(PAMP, 50, "930", "2023-07-12T12:31:20"));

		assertThat(portfolio.unpricedPositions()).isEmpty();
	}

	private static Portfolio valued(Map<Long, BigDecimal> closes, Order... orders) {
		return Portfolio.valued(new AccountLedger(Ledgers.orders(orders)).snapshot(), new Closes(closes));
	}

}
