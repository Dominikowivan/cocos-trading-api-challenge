package com.cocos.trading.service.submission;

import java.util.function.Supplier;

public class InvalidSubmissionException extends RuntimeException {

	public InvalidSubmissionException(String message) {
		super(message);
	}

	public InvalidSubmissionException(String message, Throwable cause) {
		super(message, cause);
	}

	static <T> T refusing(Supplier<T> answer) {
		try {
			return answer.get();
		}
		catch (IllegalArgumentException refused) {
			throw new InvalidSubmissionException(refused.getMessage(), refused);
		}
	}

}
