package com.cocos.trading.model.ledger;

import java.math.BigDecimal;

import com.cocos.trading.model.instrument.InstrumentRef;

public record Holding(InstrumentRef instrument, BigDecimal quantity, BigDecimal averageCost) {

	public boolean isFlat() {
		return quantity.signum() == 0;
	}

	public boolean isShort() {
		return quantity.signum() < 0;
	}

	public BigDecimal totalCost() {
		return quantity.multiply(averageCost);
	}

}
