package com.cocos.trading.service.userwrite;

public record IdempotencyKey(String value) {

	private static final int MAX_LENGTH = 255;

	public IdempotencyKey {
		if (value == null || value.isBlank()) {
			throw new InvalidIdempotencyKeyException(
					"An 'Idempotency-Key', when sent, names the request: it is not blank");
		}
		if (value.length() > MAX_LENGTH) {
			throw new InvalidIdempotencyKeyException(
					"An 'Idempotency-Key' is at most " + MAX_LENGTH + " characters, and this one is " + value.length());
		}
	}

}
