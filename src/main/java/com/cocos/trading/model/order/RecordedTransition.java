package com.cocos.trading.model.order;

import java.util.Optional;

public record RecordedTransition(OrderStatus status, Optional<RejectionReason> rejectionReason,
		Optional<CancellationReason> cancellationReason) {

}
