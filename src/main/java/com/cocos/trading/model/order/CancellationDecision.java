package com.cocos.trading.model.order;

import java.time.LocalDateTime;

public sealed interface CancellationDecision {

	record Cancellation(OrderStatus status, CancellationReason reason,
			LocalDateTime at) implements CancellationDecision {

	}

	record NotCancellable(long orderId, OrderStatus status) implements CancellationDecision {

	}

}
