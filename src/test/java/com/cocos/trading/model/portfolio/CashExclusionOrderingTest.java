package com.cocos.trading.model.portfolio;

import static com.cocos.trading.model.ledger.Ledgers.METR;
import static com.cocos.trading.model.ledger.Ledgers.PAMP;
import static com.cocos.trading.model.ledger.Ledgers.cashIn;
import static com.cocos.trading.model.ledger.Ledgers.cashOut;
import static com.cocos.trading.model.ledger.Ledgers.filledBuy;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.ledger.AccountLedger;
import com.cocos.trading.model.ledger.Ledgers;

class CashExclusionOrderingTest {

	@Test
	void pesos_are_excluded_as_cash_before_any_price_is_looked_up() {
		Portfolio portfolio = portfolioOfAUserHoldingPesosAndShares();

		assertThat(portfolio.positions()).hasSize(2);
		assertThat(portfolio.positions()).noneSatisfy(position -> assertThat(position.instrument().isCash()).isTrue());
		assertThat(portfolio.totalAccountValue()).isPresent();
	}

	@Test
	void the_unpriced_cash_instrument_is_not_an_unpriced_position() {
		assertThat(portfolioOfAUserHoldingPesosAndShares().unpricedPositions()).isEmpty();
	}

	private static Portfolio portfolioOfAUserHoldingPesosAndShares() {
		Map<Long, BigDecimal> closes = Map.of(PAMP.instrumentId(), new BigDecimal("925.85"), METR.instrumentId(),
				new BigDecimal("229.50"));

		AccountLedger ledger = new AccountLedger(Ledgers.orders(
				cashIn(1000000, "2023-07-12T12:11:20"),
				filledBuy(PAMP, 50, "930", "2023-07-12T12:31:20"),
				filledBuy(METR, 500, "250", "2023-07-13T14:11:20"),
				cashOut(100000, "2023-07-13T12:31:20")));

		return Portfolio.valued(ledger.snapshot(), new Closes(closes));
	}

}
