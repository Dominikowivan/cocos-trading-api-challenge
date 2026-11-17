package com.cocos.trading.model.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

import com.cocos.trading.model.Price;
import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.order.CashMovement;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.OrderKind;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.order.RejectionReason;
import com.cocos.trading.model.user.UserId;

public final class CashTransfer {

	private static final BigDecimal SMALLEST_AMOUNT = BigDecimal.ONE;

	private static final BigDecimal LARGEST_AMOUNT = BigDecimal.valueOf(Integer.MAX_VALUE);

	private static final Price ONE_PESO = Price.of(BigDecimal.ONE);

	private static final OrderKind SETTLES_AT_ONCE = OrderKind.MARKET;

	private final CashMovement direction;

	private final BigDecimal amount;

	private CashTransfer(CashMovement direction, BigDecimal amount) {
		this.direction = direction;
		this.amount = amount;
	}

	public static CashTransfer of(CashMovement direction, BigDecimal amount) {
		requireAnAmount(amount);
		requireWholePesos(amount);
		BigDecimal pesos = amount.setScale(0, RoundingMode.UNNECESSARY);
		requireWithinTheLedgersBounds(pesos);
		return new CashTransfer(direction, pesos);
	}

	public BigDecimal amount() {
		return amount;
	}

	public NewOrder recordedFor(AccountSnapshot snapshot, InstrumentRef cash, UserId userId, LocalDateTime at) {
		Optional<RejectionReason> shortfall = shortfallIn(snapshot);
		OrderStatus status = shortfall.isPresent() ? OrderStatus.REJECTED : SETTLES_AT_ONCE.acceptedStatus();
		return new NewOrder(userId, cash, direction, SETTLES_AT_ONCE, status, amount, ONE_PESO, shortfall, at);
	}

	private Optional<RejectionReason> shortfallIn(AccountSnapshot snapshot) {
		if (direction == CashMovement.CASH_OUT && snapshot.availableCash().compareTo(amount) < 0) {
			return Optional.of(RejectionReason.INSUFFICIENT_FUNDS);
		}
		return Optional.empty();
	}

	private static void requireAnAmount(BigDecimal amount) {
		if (amount == null) {
			throw new IllegalArgumentException("A cash transfer states the pesos it moves in 'amount'");
		}
	}

	private static void requireWholePesos(BigDecimal amount) {
		if (amount.stripTrailingZeros().scale() > 0) {
			throw new IllegalArgumentException(
					"A cash transfer is a whole number of pesos, and " + amount.toPlainString() + " is not");
		}
	}

	private static void requireWithinTheLedgersBounds(BigDecimal pesos) {
		if (pesos.compareTo(SMALLEST_AMOUNT) < 0 || pesos.compareTo(LARGEST_AMOUNT) > 0) {
			throw new IllegalArgumentException("A cash transfer is between " + SMALLEST_AMOUNT.toPlainString() + " and "
					+ LARGEST_AMOUNT.toPlainString() + " pesos, and " + pesos.toPlainString() + " is not");
		}
	}

}
