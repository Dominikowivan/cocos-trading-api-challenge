package com.cocos.trading.service.submission;

import java.math.BigDecimal;
import java.util.StringJoiner;

import com.cocos.trading.model.DecimalPolicy;
import com.cocos.trading.service.userwrite.Fingerprint;

public record RequestFingerprint(String hex) implements Fingerprint {

	private static final String FIELD_SEPARATOR = ";";

	private static final String ABSENT = "";

	public static RequestFingerprint of(SubmissionRequest request) {
		return new RequestFingerprint(Fingerprint.hashOf(canonicalFormOf(request)));
	}

	static String canonicalFormOf(SubmissionRequest request) {
		StringJoiner form = new StringJoiner(FIELD_SEPARATOR);
		form.add("userId=" + request.userId());
		form.add("instrumentId=" + stated(request.instrumentId()));
		form.add("type=" + stated(request.type()));
		form.add("side=" + stated(request.side()));
		form.add("size=" + scaled(request.size()));
		form.add("amount=" + scaled(request.amount()));
		form.add("price=" + scaled(request.price()));
		return form.toString();
	}

	private static String stated(Object field) {
		return field == null ? ABSENT : field.toString();
	}

	private static String scaled(BigDecimal amount) {
		return amount == null ? ABSENT : DecimalPolicy.money(amount).toPlainString();
	}

}
