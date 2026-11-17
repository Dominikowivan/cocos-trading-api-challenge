package com.cocos.trading.service.userwrite;

import java.util.Optional;
import java.util.function.Supplier;

public record KeyedRequest(IdempotencyKey key, Fingerprint fingerprint) {

	public static Optional<KeyedRequest> of(String sentKey, Supplier<? extends Fingerprint> fingerprint) {
		if (sentKey == null) {
			return Optional.empty();
		}
		return Optional.of(new KeyedRequest(new IdempotencyKey(sentKey), fingerprint.get()));
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
