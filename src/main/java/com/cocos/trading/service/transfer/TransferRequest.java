package com.cocos.trading.service.transfer;

import java.math.BigDecimal;
import java.util.Set;

public record TransferRequest(long userId, String side, BigDecimal amount, Set<String> otherFields,
		String idempotencyKey) {

	public TransferRequest {
		otherFields = Set.copyOf(otherFields);
	}

	public TransferRequest(long userId, String side, BigDecimal amount) {
		this(userId, side, amount, Set.of(), null);
	}

}
