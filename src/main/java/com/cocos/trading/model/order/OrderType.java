package com.cocos.trading.model.order;

public enum OrderType {

	MARKET {
		@Override
		public OrderStatus acceptedStatus() {
			return OrderStatus.FILLED;
		}
	},

	LIMIT {
		@Override
		public OrderStatus acceptedStatus() {
			return OrderStatus.NEW;
		}
	};

	public abstract OrderStatus acceptedStatus();

}
