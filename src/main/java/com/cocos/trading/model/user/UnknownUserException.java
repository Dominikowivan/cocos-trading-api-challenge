package com.cocos.trading.model.user;

public class UnknownUserException extends RuntimeException {

	public UnknownUserException(UserId userId) {
		super("User " + userId.value() + " does not exist");
	}

}
