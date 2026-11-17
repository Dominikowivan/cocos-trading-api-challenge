package com.cocos.trading.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cocos.trading.model.order.RejectionReason;
import com.cocos.trading.service.submission.SubmittedOrder;

public record OrderResponse(long orderId, long userId, long instrumentId, String ticker, String type, String side,
		String status, int size, BigDecimal price, String rejectionReason, LocalDateTime submittedAt) {

	public static OrderResponse of(SubmittedOrder order) {
		return new OrderResponse(order.orderId(), order.userId().value(), order.instrument().instrumentId(),
				order.instrument().ticker(), order.kind().name(), order.side().name(), order.status().name(),
				order.size().intValueExact(), order.price(),
				order.rejectionReason().map(RejectionReason::name).orElse(null), order.submittedAt());
	}

}
