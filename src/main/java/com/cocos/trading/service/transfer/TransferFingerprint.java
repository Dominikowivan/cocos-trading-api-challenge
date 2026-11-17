package com.cocos.trading.service.transfer;

import java.util.StringJoiner;

import com.cocos.trading.service.userwrite.Fingerprint;

record TransferFingerprint(String hex) implements Fingerprint {

	private static final String FIELD_SEPARATOR = ";";

	private static final String OPERATION = "operation=CASH_TRANSFER";

	static TransferFingerprint of(TransferRequest request) {
		return new TransferFingerprint(Fingerprint.hashOf(canonicalFormOf(request)));
	}

	static String canonicalFormOf(TransferRequest request) {
		StringJoiner form = new StringJoiner(FIELD_SEPARATOR);
		form.add(OPERATION);
		form.add("userId=" + request.userId());
		form.add("side=" + request.side());
		form.add("amount=" + request.amount().stripTrailingZeros().toPlainString());
		return form.toString();
	}

}
