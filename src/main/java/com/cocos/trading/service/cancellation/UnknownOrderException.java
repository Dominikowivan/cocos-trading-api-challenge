package com.cocos.trading.service.cancellation;

import com.cocos.trading.model.user.UserId;

public class UnknownOrderException extends RuntimeException {

	public UnknownOrderException(long orderId, UserId userId) {
		super("Order " + orderId + " does not exist for user " + userId.value());
	}

}
