package com.cocos.trading.model.transfer;

import static com.cocos.trading.model.ledger.Ledgers.ARS;
import static com.cocos.trading.model.ledger.Ledgers.PAMP;
import static com.cocos.trading.model.ledger.Ledgers.cashIn;
import static com.cocos.trading.model.ledger.Ledgers.filledBuy;
import static com.cocos.trading.model.ledger.Ledgers.orders;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.ledger.AccountLedger;
import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.ledger.Ledgers;
import com.cocos.trading.model.order.CashMovement;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.OrderKind;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.order.RejectionReason;
import com.cocos.trading.model.user.UserId;

class CashTransferDecisionTest {

	private static final UserId USER = new UserId(2);

	private static final LocalDateTime AT = LocalDateTime.parse("2026-11-17T15:04:05.123456");

	private static final AccountSnapshot NOTHING = new AccountLedger(orders()).snapshot();

	private static final AccountSnapshot ONE_HUNDRED_THOUSAND = new AccountLedger(
			orders(cashIn(100000, "2026-11-17T10:00:00")))
		.snapshot();

	private static final AccountSnapshot OVERDRAWN = new AccountLedger(
			orders(filledBuy(PAMP, 10, "925.85", "2026-11-17T10:00:00")))
		.snapshot();

	@Test
	void a_deposit_is_filled_whatever_the_available_cash() {
		assertThat(deposit(500).recordedFor(NOTHING, ARS, USER, AT).status()).isEqualTo(OrderStatus.FILLED);
		assertThat(deposit(500).recordedFor(OVERDRAWN, ARS, USER, AT).status()).isEqualTo(OrderStatus.FILLED);
		assertThat(deposit(500).recordedFor(NOTHING, ARS, USER, AT).rejectionReason()).isEmpty();
	}

	@Test
	void a_withdrawal_of_exactly_the_available_cash_is_filled() {
		NewOrder order = withdrawal(100000).recordedFor(ONE_HUNDRED_THOUSAND, ARS, USER, AT);

		assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
		assertThat(order.rejectionReason()).isEmpty();
	}

	@Test
	void a_withdrawal_one_peso_above_the_available_cash_is_rejected_for_funds() {
		NewOrder order = withdrawal(100001).recordedFor(ONE_HUNDRED_THOUSAND, ARS, USER, AT);

		assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
		assertThat(order.rejectionReason()).contains(RejectionReason.INSUFFICIENT_FUNDS);
	}

	@Test
	void reserved_pesos_cannot_be_withdrawn() {
		AccountSnapshot seeded = Ledgers.seededUserOne().snapshot();

		assertThat(withdrawal(627500).recordedFor(seeded, ARS, USER, AT).status()).isEqualTo(OrderStatus.FILLED);
		assertThat(withdrawal(627501).recordedFor(seeded, ARS, USER, AT).status()).isEqualTo(OrderStatus.REJECTED);
	}

	@Test
	void with_a_negative_available_cash_every_withdrawal_is_rejected() {
		assertThat(OVERDRAWN.availableCash()).isNegative();
		assertThat(withdrawal(1).recordedFor(OVERDRAWN, ARS, USER, AT).status()).isEqualTo(OrderStatus.REJECTED);
	}

	@Test
	void every_transfer_is_a_market_order_of_its_amount_at_one_peso_on_the_cash_instrument() {
		for (NewOrder order : new NewOrder[] { deposit(250).recordedFor(NOTHING, ARS, USER, AT),
				withdrawal(250).recordedFor(NOTHING, ARS, USER, AT) }) {
			assertThat(order.kind()).isEqualTo(OrderKind.MARKET);
			assertThat(order.price().amount()).isEqualByComparingTo(BigDecimal.ONE);
			assertThat(order.size()).isEqualByComparingTo("250");
			assertThat(order.instrument()).isEqualTo(ARS);
			assertThat(order.userId()).isEqualTo(USER);
			assertThat(order.occurredAt()).isEqualTo(AT);
		}
	}

	@Test
	void the_order_carries_the_direction_of_the_transfer() {
		assertThat(deposit(1).recordedFor(NOTHING, ARS, USER, AT).side()).isEqualTo(CashMovement.CASH_IN);
		assertThat(withdrawal(1).recordedFor(NOTHING, ARS, USER, AT).side()).isEqualTo(CashMovement.CASH_OUT);
	}

	private static CashTransfer deposit(long pesos) {
		return CashTransfer.of(CashMovement.CASH_IN, BigDecimal.valueOf(pesos));
	}

	private static CashTransfer withdrawal(long pesos) {
		return CashTransfer.of(CashMovement.CASH_OUT, BigDecimal.valueOf(pesos));
	}

}
