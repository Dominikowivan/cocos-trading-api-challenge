package com.cocos.trading.model.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import com.cocos.trading.model.Price;
import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.user.UserId;

public record NewOrder(UserId userId, InstrumentRef instrument, LedgerSide side, OrderKind kind, OrderStatus status,
		BigDecimal size, Price price, Optional<RejectionReason> rejectionReason, LocalDateTime occurredAt) {

	public NewOrder rejectedFor(RejectionReason reason) {
		return new NewOrder(userId, instrument, side, kind, status, size, price, Optional.of(reason), occurredAt);
	}

}
