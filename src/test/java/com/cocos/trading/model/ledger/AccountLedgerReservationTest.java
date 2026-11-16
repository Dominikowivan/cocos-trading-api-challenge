package com.cocos.trading.model.ledger;

import static com.cocos.trading.model.ledger.Ledgers.LOMA;
import static com.cocos.trading.model.ledger.Ledgers.METR;
import static com.cocos.trading.model.ledger.Ledgers.cashIn;
import static com.cocos.trading.model.ledger.Ledgers.filledBuy;
import static com.cocos.trading.model.ledger.Ledgers.openBuyLimit;
import static com.cocos.trading.model.ledger.Ledgers.openBuyMarket;
import static com.cocos.trading.model.ledger.Ledgers.openSellLimit;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountLedgerReservationTest {

	@Test
	void an_open_buy_limit_reserves_size_times_price_and_reduces_available_cash() {
		AccountSnapshot snapshot = new AccountLedger(Ledgers.orders(
				cashIn(1000000, "2023-07-12T12:11:20"),
				openBuyLimit(LOMA, 50, "710", "2023-07-12T15:14:20"))).snapshot();

		assertThat(snapshot.settledCash()).isEqualByComparingTo("1000000.00");
		assertThat(snapshot.availableCash()).isEqualByComparingTo("964500.00");
	}

	@Test
	void an_open_sell_limit_reserves_shares_and_leaves_cash_untouched() {
		AccountSnapshot snapshot = new AccountLedger(Ledgers.orders(
				cashIn(1000000, "2023-07-12T12:11:20"),
				filledBuy(METR, 500, "250", "2023-07-13T14:11:20"),
				openSellLimit(METR, 200, "260", "2023-07-13T15:11:20"))).snapshot();

		assertThat(snapshot.availableCash()).isEqualByComparingTo("875000.00");
		assertThat(snapshot.settledCash()).isEqualByComparingTo("875000.00");
		assertThat(snapshot.availableQuantityOf(METR.instrumentId())).isEqualByComparingTo("300");
	}

	@Test
	void an_open_market_order_reserves_nothing() {
		AccountSnapshot snapshot = new AccountLedger(Ledgers.orders(
				cashIn(1000000, "2023-07-12T12:11:20"),
				openBuyMarket(LOMA, 50, "710", "2023-07-12T15:14:20"))).snapshot();

		assertThat(snapshot.availableCash()).isEqualByComparingTo("1000000.00");
	}

	@Test
	void a_held_instrument_with_nothing_reserved_is_fully_available() {
		AccountSnapshot snapshot = new AccountLedger(Ledgers.orders(
				filledBuy(METR, 500, "250", "2023-07-13T14:11:20"))).snapshot();

		assertThat(snapshot.availableQuantityOf(METR.instrumentId())).isEqualByComparingTo("500");
	}

	@Test
	void an_instrument_that_was_never_traded_has_nothing_available() {
		AccountSnapshot snapshot = new AccountLedger(Ledgers.orders(
				filledBuy(METR, 500, "250", "2023-07-13T14:11:20"))).snapshot();

		assertThat(snapshot.availableQuantityOf(LOMA.instrumentId())).isEqualByComparingTo("0");
	}

}
