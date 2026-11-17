package com.cocos.trading.service.cancellation;

import com.cocos.trading.model.user.UserId;

record RequestedCancellation(UserId userId, long orderId) {

	private static final long SMALLEST_ID = 1;

	static RequestedCancellation read(CancellationRequest request) {
		requirePositive("A user id", request.userId());
		requirePositive("An order id", request.orderId());
		return new RequestedCancellation(new UserId(request.userId()), request.orderId());
	}

	private static void requirePositive(String which, long id) {
		if (id < SMALLEST_ID) {
			throw new InvalidCancellationException(which + " is a positive whole number, and " + id + " is not");
		}
	}

}
