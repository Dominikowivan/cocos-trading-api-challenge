package com.cocos.trading.service.userwrite;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public interface Fingerprint {

	String hex();

	default boolean matches(String storedHex) {
		return hex().equals(storedHex);
	}

	static String hashOf(String canonicalForm) {
		return HexFormat.of().formatHex(digestOf(canonicalForm));
	}

	private static byte[] digestOf(String canonicalForm) {
		String algorithm = "SHA-256";
		try {
			return MessageDigest.getInstance(algorithm).digest(canonicalForm.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException unavailable) {
			throw new IllegalStateException(algorithm + " is required of every Java platform", unavailable);
		}
	}

}
