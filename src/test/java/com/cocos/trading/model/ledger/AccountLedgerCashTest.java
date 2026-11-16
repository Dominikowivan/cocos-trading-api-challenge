package com.cocos.trading.model.ledger;

import static com.cocos.trading.model.ledger.Ledgers.BMA;
import static com.cocos.trading.model.ledger.Ledgers.LOMA;
import static com.cocos.trading.model.ledger.Ledgers.METR;
import static com.cocos.trading.model.ledger.Ledgers.PAMP;
import static com.cocos.trading.model.ledger.Ledgers.cancelledBuyLimit;
import static com.cocos.trading.model.ledger.Ledgers.cashIn;
import static com.cocos.trading.model.ledger.Ledgers.cashOut;
import static com.cocos.trading.model.ledger.Ledgers.filledBuy;
import static com.cocos.trading.model.ledger.Ledgers.filledSell;
import static com.cocos.trading.model.ledger.Ledgers.openBuyLimit;
import static com.cocos.trading.model.ledger.Ledgers.rejectedSell;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountLedgerCashTest {

	@Test
	void settled_cash_is_the_pesos_effect_of_every_filled_order() {
		AccountSnapshot snapshot = seededLedger().snapshot();

		assertThat(snapshot.settledCash()).isEqualByComparingTo("753000.00");
	}

	@Test
	void settled_cash_is_not_the_sum_of_the_pesos_instruments_rows() {
		AccountSnapshot snapshot = seededLedger().snapshot();

		assertThat(snapshot.settledCash()).isNotEqualByComparingTo("900000.00");
	}

	@Test
	void a_cash_inflow_adds_and_an_outflow_subtracts() {
		AccountSnapshot snapshot = new AccountLedger(Ledgers.orders(
				cashIn(1000000, "2023-07-12T12:11:20"),
				cashOut(100000, "2023-07-13T12:31:20"))).snapshot();

		assertThat(snapshot.settledCash()).isEqualByComparingTo("900000.00");
	}

	@Test
	void a_purchase_subtracts_and_a_sale_adds() {
		AccountSnapshot snapshot = new AccountLedger(Ledgers.orders(
				filledBuy(PAMP, 50, "930", "2023-07-12T12:31:20"),
				filledSell(PAMP, 10, "940", "2023-07-12T14:51:20"))).snapshot();

		assertThat(snapshot.settledCash()).isEqualByComparingTo("-37100.00");
	}

	@Test
	void unsettled_orders_move_no_cash() {
		AccountSnapshot snapshot = new AccountLedger(Ledgers.orders(
				cashIn(1000, "2023-07-12T12:11:20"),
				openBuyLimit(PAMP, 50, "920", "2023-07-12T14:21:20"),
				cancelledBuyLimit(PAMP, 50, "920", "2023-07-12T14:31:20"),
				rejectedSell(PAMP, 100, "950", "2023-07-12T16:11:20"))).snapshot();

		assertThat(snapshot.settledCash()).isEqualByComparingTo("1000.00");
	}

	@Test
	void a_user_with_no_orders_folds_to_zero_cash() {
		AccountSnapshot snapshot = new AccountLedger(Ledgers.orders()).snapshot();

		assertThat(snapshot.settledCash()).isEqualByComparingTo("0.00");
		assertThat(snapshot.availableCash()).isEqualByComparingTo("0.00");
		assertThat(snapshot.holdings()).isEmpty();
	}

	private static AccountLedger seededLedger() {
		return new AccountLedger(Ledgers.orders(
				cashIn(1000000, "2023-07-12T12:11:20"),
				filledBuy(PAMP, 50, "930", "2023-07-12T12:31:20"),
				filledSell(PAMP, 10, "940", "2023-07-12T14:51:20"),
				openBuyLimit(LOMA, 50, "710", "2023-07-12T15:14:20"),
				openBuyLimit(BMA, 60, "1500", "2023-07-13T11:13:20"),
				cashOut(100000, "2023-07-13T12:31:20"),
				filledBuy(BMA, 20, "1540", "2023-07-13T12:51:20"),
				filledBuy(METR, 500, "250", "2023-07-13T14:11:20"),
				filledSell(BMA, 30, "1530", "2023-07-13T15:13:20")));
	}

}
