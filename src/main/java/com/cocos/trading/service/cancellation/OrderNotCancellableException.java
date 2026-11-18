package com.cocos.trading.service.cancellation;

import com.cocos.trading.model.order.cancellation.CancellationDecision;

public class OrderNotCancellableException extends RuntimeException {

	private static final String ONLY_NEW = "only a NEW order can be cancelled";

	private OrderNotCancellableException(String message) {
		super(message);
	}

	static OrderNotCancellableException of(CancellationDecision.NotCancellable refusal) {
		return new OrderNotCancellableException(
				"Order " + refusal.orderId() + " is " + refusal.status().name() + "; " + ONLY_NEW);
	}

	static OrderNotCancellableException leftNewBeforeItsTransition(long orderId) {
		return new OrderNotCancellableException("Order " + orderId + " is no longer NEW; " + ONLY_NEW);
	}

}
