package com.cocos.trading.model.order;

import java.math.BigDecimal;

public enum CashMovement implements LedgerSide {

	CASH_IN {
		@Override
		public BigDecimal cashSign() {
			return INFLOW;
		}
	},

	CASH_OUT {
		@Override
		public BigDecimal cashSign() {
			return OUTFLOW;
		}
	};

	private static final BigDecimal UNCHANGED = BigDecimal.ZERO;

	@Override
	public BigDecimal positionSign() {
		return UNCHANGED;
	}

}
