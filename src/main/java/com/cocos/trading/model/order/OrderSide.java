package com.cocos.trading.model.order;

import java.math.BigDecimal;

import com.cocos.trading.model.Price;

public enum OrderSide implements LedgerSide {

	BUY {
		@Override
		public BigDecimal cashSign() {
			return OUTFLOW;
		}

		@Override
		public BigDecimal positionSign() {
			return INCREASE;
		}

		@Override
		public BigDecimal committableValue(AvailableBalance available, Price price) {
			return available.cash();
		}

		@Override
		public RejectionReason whatRanOut() {
			return RejectionReason.INSUFFICIENT_FUNDS;
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

		@Override
		public BigDecimal committableValue(AvailableBalance available, Price price) {
			return price.times(available.shares());
		}

		@Override
		public RejectionReason whatRanOut() {
			return RejectionReason.INSUFFICIENT_HOLDINGS;
		}
	};

	private static final BigDecimal INCREASE = BigDecimal.ONE;

	private static final BigDecimal DECREASE = BigDecimal.ONE.negate();

	public abstract BigDecimal committableValue(AvailableBalance available, Price price);

	public abstract RejectionReason whatRanOut();

}
