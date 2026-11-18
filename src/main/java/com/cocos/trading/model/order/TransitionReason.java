package com.cocos.trading.model.order;

import java.util.Optional;

import com.cocos.trading.model.order.cancellation.CancellationReason;

public sealed interface TransitionReason {

	Optional<RejectionReason> rejection();

	record Rejected(RejectionReason reason) implements TransitionReason {

		@Override
		public Optional<RejectionReason> rejection() {
			return Optional.of(reason);
		}

	}

	record Cancelled(CancellationReason reason) implements TransitionReason {

		@Override
		public Optional<RejectionReason> rejection() {
			return Optional.empty();
		}

	}

	record Unstated() implements TransitionReason {

		@Override
		public Optional<RejectionReason> rejection() {
			return Optional.empty();
		}

	}

}
