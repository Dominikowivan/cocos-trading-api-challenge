package com.cocos.trading.model.order;

import java.util.Set;

public enum OrderStatus {

	NEW, FILLED, REJECTED, CANCELLED;

	public static Set<OrderStatus> contributing() {
		return Set.of(FILLED, NEW);
	}

	public boolean isSettled() {
		return this == FILLED;
	}

	public boolean isOpen() {
		return this == NEW;
	}

}
