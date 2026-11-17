package com.cocos.trading.service.submission;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.StringJoiner;

import com.cocos.trading.model.DecimalPolicy;

public record RequestFingerprint(String hex) {

	private static final String ALGORITHM = "SHA-256";

	private static final String FIELD_SEPARATOR = ";";

	private static final String ABSENT = "";

	public static RequestFingerprint of(SubmissionRequest request) {
		return new RequestFingerprint(hashOf(canonicalFormOf(request)));
	}

	public boolean matches(String storedHex) {
		return hex.equals(storedHex);
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

	private static String hashOf(String canonicalForm) {
		return HexFormat.of().formatHex(digestOf(canonicalForm));
	}

	private static byte[] digestOf(String canonicalForm) {
		try {
			return MessageDigest.getInstance(ALGORITHM).digest(canonicalForm.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException unavailable) {
			throw new IllegalStateException(ALGORITHM + " is required of every Java platform", unavailable);
		}
	}

}
