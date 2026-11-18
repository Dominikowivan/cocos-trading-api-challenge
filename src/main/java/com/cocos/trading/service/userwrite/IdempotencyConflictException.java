package com.cocos.trading.service.userwrite;

public class IdempotencyConflictException extends RuntimeException {

	public IdempotencyConflictException(String key) {
		super("The 'Idempotency-Key' " + key
				+ " already names a different request for this user; one key, one request");
	}

}
