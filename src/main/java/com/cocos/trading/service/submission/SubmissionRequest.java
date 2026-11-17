package com.cocos.trading.service.submission;

import java.math.BigDecimal;

public record SubmissionRequest(long userId, Long instrumentId, String type, String side, BigDecimal size,
		BigDecimal amount, BigDecimal price, String idempotencyKey) {

	public SubmissionRequest(long userId, Long instrumentId, String type, String side, BigDecimal size,
			BigDecimal amount, BigDecimal price) {
		this(userId, instrumentId, type, side, size, amount, price, null);
	}

}
