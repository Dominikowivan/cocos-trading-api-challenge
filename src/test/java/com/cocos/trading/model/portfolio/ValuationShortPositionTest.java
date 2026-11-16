package com.cocos.trading.model.portfolio;

import static com.cocos.trading.model.ledger.Ledgers.BMA;
import static com.cocos.trading.model.ledger.Ledgers.PAMP;
import static com.cocos.trading.model.ledger.Ledgers.filledBuy;
import static com.cocos.trading.model.ledger.Ledgers.filledSell;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.ledger.AccountLedger;
import com.cocos.trading.model.ledger.Ledgers;
import com.cocos.trading.model.order.Order;

class ValuationShortPositionTest {

	@Test
	void a_short_holding_is_listed_with_its_negative_quantity() {
		Portfolio portfolio = valuedOver(filledBuy(BMA, 20, "1540", "2023-07-13T12:51:20"),
				filledSell(BMA, 30, "1530", "2023-07-13T15:13:20"));

		assertThat(portfolio.positions()).hasSize(1);
		assertThat(portfolio.positions().getFirst().quantity()).isEqualByComparingTo("-10");
	}

	@Test
	void a_short_holding_is_reported_exactly_once() {
		Portfolio portfolio = valuedOver(filledBuy(BMA, 20, "1540", "2023-07-13T12:51:20"),
				filledSell(BMA, 30, "1530", "2023-07-13T15:13:20"));

		assertThat(portfolio.shortPositions()).hasSize(1);
		assertThat(portfolio.shortPositions().getFirst().instrument().ticker()).isEqualTo("BMA");
		assertThat(portfolio.shortPositions().getFirst().quantity()).isEqualByComparingTo("-10");
	}

	@Test
	void a_long_holding_reports_nothing() {
		Portfolio portfolio = valuedOver(filledBuy(PAMP, 50, "930", "2023-07-12T12:31:20"));

		assertThat(portfolio.shortPositions()).isEmpty();
	}

	private static Portfolio valuedOver(Order... orders) {
		Map<Long, BigDecimal> closes = Map.of(BMA.instrumentId(), new BigDecimal("1502.80"), PAMP.instrumentId(),
				new BigDecimal("925.85"));
		return Portfolio.valued(new AccountLedger(Ledgers.orders(orders)).snapshot(), new Closes(closes));
	}

}
