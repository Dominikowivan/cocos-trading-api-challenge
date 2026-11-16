package com.cocos.trading.model.order;

import java.math.BigDecimal;

public enum OrderSide {

	BUY {
		@Override
		public BigDecimal cashSign() {
			return OUTFLOW;
		}

		@Override
		public BigDecimal positionSign() {
			return INCREASE;
		}
	},

	SELL {
		@Override
		public BigDecimal cashSign() {
			return INFLOW;
		}

		@Override
		public BigDecimal positionSign() {
			return DECREASE;
		}
	},

	CASH_IN {
		@Override
		public BigDecimal cashSign() {
			return INFLOW;
		}

		@Override
		public BigDecimal positionSign() {
			throw new UnsupportedOperationException(name() + " moves cash, never a position");
		}
	},

	CASH_OUT {
		@Override
		public BigDecimal cashSign() {
			return OUTFLOW;
		}

		@Override
		public BigDecimal positionSign() {
			throw new UnsupportedOperationException(name() + " moves cash, never a position");
		}
	};

	private static final BigDecimal INFLOW = BigDecimal.ONE;

	private static final BigDecimal OUTFLOW = BigDecimal.ONE.negate();

	private static final BigDecimal INCREASE = BigDecimal.ONE;

	private static final BigDecimal DECREASE = BigDecimal.ONE.negate();

	public abstract BigDecimal cashSign();

	public abstract BigDecimal positionSign();

}
