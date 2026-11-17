package com.cocos.trading.service.submission;

import static com.cocos.trading.service.submission.InvalidSubmissionException.refusing;

record IdempotentRequest(IdempotencyKey key, RequestFingerprint fingerprint) {

	static IdempotentRequest of(SubmissionRequest request) {
		return new IdempotentRequest(refusing(() -> new IdempotencyKey(request.idempotencyKey())),
				RequestFingerprint.of(request));
	}

	String keyValue() {
		return key.value();
	}

	String fingerprintHex() {
		return fingerprint.hex();
	}

	boolean wasSentBefore(String storedFingerprint) {
		return fingerprint.matches(storedFingerprint);
	}

}
