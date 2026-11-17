package com.cocos.trading.model.order.cancellation;

import java.time.LocalDateTime;

import com.cocos.trading.model.order.OrderStatus;

public final class CancellationRule {

	private CancellationRule() {
	}

	public static CancellationDecision decide(long orderId, OrderStatus current, LocalDateTime at) {
		if (current.isOpen()) {
			return new CancellationDecision.Cancellation(OrderStatus.CANCELLED, CancellationReason.USER_REQUESTED, at);
		}
		return new CancellationDecision.NotCancellable(orderId, current);
	}

}
