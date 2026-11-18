package com.cocos.trading.service.userwrite;

public class InvalidIdempotencyKeyException extends RuntimeException {

	public InvalidIdempotencyKeyException(String message) {
		super(message);
	}

}
