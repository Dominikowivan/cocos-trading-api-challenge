package com.cocos.trading.service.cancellation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.order.LedgerSide;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.OrderKind;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.user.UserId;
import com.cocos.trading.service.userwrite.WrittenOrder;

public record CancelledOrder(long orderId, UserId userId, InstrumentRef instrument, LedgerSide side, OrderKind kind,
		OrderStatus status, BigDecimal size, BigDecimal price, LocalDateTime submittedAt) implements WrittenOrder {

	public static CancelledOrder of(long orderId, NewOrder order, OrderStatus status) {
		return new CancelledOrder(orderId, order.userId(), order.instrument(), order.side(), order.kind(), status,
				order.size(), order.price().amount(), order.occurredAt());
	}

}
