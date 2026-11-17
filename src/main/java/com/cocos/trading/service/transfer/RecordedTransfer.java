package com.cocos.trading.service.transfer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import com.cocos.trading.model.order.CashMovement;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.order.RejectionReason;
import com.cocos.trading.model.user.UserId;
import com.cocos.trading.service.userwrite.AppendedOrder;

public record RecordedTransfer(long orderId, UserId userId, CashMovement side, BigDecimal amount, OrderStatus status,
		Optional<RejectionReason> rejectionReason, LocalDateTime transferredAt) implements AppendedOrder {

	public static RecordedTransfer of(long orderId, NewOrder order) {
		return new RecordedTransfer(orderId, order.userId(), movementOf(orderId, order), order.size(), order.status(),
				order.rejectionReason(), order.occurredAt());
	}

	private static CashMovement movementOf(long orderId, NewOrder order) {
		if (order.side() instanceof CashMovement movement) {
			return movement;
		}
		throw new IllegalStateException("order " + orderId + " is a " + order.side().name() + ", not a cash transfer");
	}

}
