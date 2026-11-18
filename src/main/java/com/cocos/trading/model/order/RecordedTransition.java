package com.cocos.trading.model.order;

import java.util.Optional;

public record RecordedTransition(OrderStatus status, TransitionReason reason) {

	public Optional<RejectionReason> rejectionReason() {
		return reason.rejection();
	}

}
