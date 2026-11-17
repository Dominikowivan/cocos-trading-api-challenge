package com.cocos.trading.service.cancellation;

import java.util.StringJoiner;

import com.cocos.trading.service.userwrite.Fingerprint;

record CancellationFingerprint(String hex) implements Fingerprint {

	private static final String FIELD_SEPARATOR = ";";

	private static final String OPERATION = "operation=ORDER_CANCELLATION";

	static CancellationFingerprint of(CancellationRequest request) {
		return new CancellationFingerprint(Fingerprint.hashOf(canonicalFormOf(request)));
	}

	static String canonicalFormOf(CancellationRequest request) {
		StringJoiner form = new StringJoiner(FIELD_SEPARATOR);
		form.add(OPERATION);
		form.add("userId=" + request.userId());
		form.add("orderId=" + request.orderId());
		return form.toString();
	}

}
