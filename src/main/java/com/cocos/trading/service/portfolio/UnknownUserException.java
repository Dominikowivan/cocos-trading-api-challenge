package com.cocos.trading.service.portfolio;

import com.cocos.trading.model.user.UserId;

public class UnknownUserException extends RuntimeException {

	public UnknownUserException(UserId userId) {
		super("User " + userId.value() + " does not exist");
	}

}
