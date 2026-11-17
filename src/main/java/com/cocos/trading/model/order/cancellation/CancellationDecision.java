package com.cocos.trading.model.order.cancellation;

import java.time.LocalDateTime;

import com.cocos.trading.model.order.OrderStatus;

public sealed interface CancellationDecision {

	record Cancellation(OrderStatus status, CancellationReason reason,
			LocalDateTime at) implements CancellationDecision {

	}

	record NotCancellable(long orderId, OrderStatus status) implements CancellationDecision {

	}

}
