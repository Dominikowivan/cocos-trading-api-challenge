package com.cocos.trading.service.submission;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.order.LedgerSide;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.OrderKind;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.order.RejectionReason;
import com.cocos.trading.model.user.UserId;
import com.cocos.trading.service.userwrite.AppendedOrder;

public record SubmittedOrder(long orderId, UserId userId, InstrumentRef instrument, LedgerSide side, OrderKind kind,
		OrderStatus status, BigDecimal size, BigDecimal price, Optional<RejectionReason> rejectionReason,
		LocalDateTime submittedAt) implements AppendedOrder {

	public static SubmittedOrder of(long orderId, NewOrder order) {
		return new SubmittedOrder(orderId, order.userId(), order.instrument(), order.side(), order.kind(),
				order.status(), order.size(), order.price().amount(), order.rejectionReason(), order.occurredAt());
	}

}
