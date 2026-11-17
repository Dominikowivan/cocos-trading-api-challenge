package com.cocos.trading.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cocos.trading.model.DecimalPolicy;
import com.cocos.trading.model.order.RejectionReason;
import com.cocos.trading.service.transfer.RecordedTransfer;

public record CashTransferResponse(long orderId, long userId, String side, BigDecimal amount, String status,
		String rejectionReason, LocalDateTime transferredAt) {

	public static CashTransferResponse from(RecordedTransfer transfer) {
		return new CashTransferResponse(transfer.orderId(), transfer.userId().value(), transfer.side().name(),
				DecimalPolicy.money(transfer.amount()), transfer.status().name(),
				transfer.rejectionReason().map(RejectionReason::name).orElse(null), transfer.transferredAt());
	}

}
