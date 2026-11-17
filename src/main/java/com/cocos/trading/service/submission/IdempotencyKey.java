package com.cocos.trading.service.submission;

record IdempotencyKey(String value) {

	private static final int MAX_LENGTH = 255;

	IdempotencyKey {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("An 'Idempotency-Key', when sent, names the request: it is not blank");
		}
		if (value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException(
					"An 'Idempotency-Key' is at most " + MAX_LENGTH + " characters, and this one is " + value.length());
		}
	}

}
