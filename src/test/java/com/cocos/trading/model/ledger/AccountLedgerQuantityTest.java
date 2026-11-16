package com.cocos.trading.model.ledger;

import static com.cocos.trading.model.ledger.Ledgers.BMA;
import static com.cocos.trading.model.ledger.Ledgers.PAMP;
import static com.cocos.trading.model.ledger.Ledgers.cancelledBuyLimit;
import static com.cocos.trading.model.ledger.Ledgers.cashIn;
import static com.cocos.trading.model.ledger.Ledgers.cashOut;
import static com.cocos.trading.model.ledger.Ledgers.filledBuy;
import static com.cocos.trading.model.ledger.Ledgers.filledSell;
import static com.cocos.trading.model.ledger.Ledgers.openBuyLimit;
import static com.cocos.trading.model.ledger.Ledgers.rejectedSell;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.order.OrderStatus;

class AccountLedgerQuantityTest {

	@Test
	void buys_add_and_sells_subtract_over_settled_orders() {
		AccountSnapshot snapshot = new AccountLedger(Ledgers.orders(filledBuy(PAMP, 50, "930", "2023-07-12T12:31:20"),
				filledSell(PAMP, 10, "940", "2023-07-12T14:51:20")))
			.snapshot();

		assertThat(quantityOf(snapshot, PAMP)).isEqualByComparingTo("40");
	}

	@Test
	void open_cancelled_and_rejected_orders_change_no_quantity() {
		AccountSnapshot snapshot = new AccountLedger(Ledgers.orders(filledBuy(PAMP, 50, "930", "2023-07-12T12:31:20"),
				openBuyLimit(PAMP, 50, "920", "2023-07-12T14:21:20"),
				cancelledBuyLimit(PAMP, 50, "920", "2023-07-12T14:31:20"),
				rejectedSell(PAMP, 100, "950", "2023-07-12T16:11:20")))
			.snapshot();

		assertThat(quantityOf(snapshot, PAMP)).isEqualByComparingTo("50");
	}

	@Test
	void only_filled_and_new_orders_contribute_to_the_fold() {
		assertThat(OrderStatus.contributing()).containsExactlyInAnyOrder(OrderStatus.FILLED, OrderStatus.NEW);
	}

	@Test
	void a_cash_instrument_is_never_a_holding() {
		AccountSnapshot snapshot = new AccountLedger(
				Ledgers.orders(cashIn(1000000, "2023-07-12T12:11:20"), cashOut(100000, "2023-07-13T12:31:20")))
			.snapshot();

		assertThat(snapshot.holdings()).isEmpty();
	}

	@Test
	void a_net_zero_instrument_is_not_listed() {
		AccountSnapshot snapshot = new AccountLedger(Ledgers.orders(filledBuy(BMA, 20, "1540", "2023-07-13T12:51:20"),
				filledSell(BMA, 20, "1530", "2023-07-13T15:13:20")))
			.snapshot();

		assertThat(snapshot.holdings()).isEmpty();
	}

	@Test
	void a_short_position_is_folded_as_a_negative_quantity() {
		AccountSnapshot snapshot = new AccountLedger(Ledgers.orders(filledBuy(BMA, 20, "1540", "2023-07-13T12:51:20"),
				filledSell(BMA, 30, "1530", "2023-07-13T15:13:20")))
			.snapshot();

		assertThat(quantityOf(snapshot, BMA)).isEqualByComparingTo("-10");
	}

	private static BigDecimal quantityOf(AccountSnapshot snapshot, InstrumentRef instrument) {
		return snapshot.holdings()
			.stream()
			.filter(holding -> holding.instrument().equals(instrument))
			.findFirst()
			.orElseThrow()
			.quantity();
	}

}
