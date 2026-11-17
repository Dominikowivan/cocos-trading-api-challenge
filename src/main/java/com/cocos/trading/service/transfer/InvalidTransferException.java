package com.cocos.trading.service.transfer;

import java.util.function.Supplier;

public class InvalidTransferException extends RuntimeException {

	public InvalidTransferException(String message) {
		super(message);
	}

	public InvalidTransferException(String message, Throwable cause) {
		super(message, cause);
	}

	static <T> T refusing(Supplier<T> answer) {
		try {
			return answer.get();
		}
		catch (IllegalArgumentException refused) {
			throw new InvalidTransferException(refused.getMessage(), refused);
		}
	}

}
