package com.cocos.trading.service.cancellation;

public record CancellationRequest(long userId, long orderId, String idempotencyKey) {

	public CancellationRequest(long userId, long orderId) {
		this(userId, orderId, null);
	}

}
