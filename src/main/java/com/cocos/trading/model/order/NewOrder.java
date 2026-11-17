package com.cocos.trading.model.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import com.cocos.trading.model.Price;
import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.user.UserId;

public record NewOrder(UserId userId, InstrumentRef instrument, LedgerSide side, OrderKind kind, OrderStatus status,
		BigDecimal size, Price price, Optional<RejectionReason> rejectionReason, LocalDateTime occurredAt) {

	public NewOrder as(RecordedTransition outcome) {
		return new NewOrder(userId, instrument, side, kind, outcome.status(), size, price, outcome.rejectionReason(),
				occurredAt);
	}

}
