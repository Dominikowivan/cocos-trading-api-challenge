package com.cocos.trading.model.portfolio;

import java.math.BigDecimal;
import java.util.Optional;

import com.cocos.trading.model.instrument.InstrumentRef;

public record Position(InstrumentRef instrument, BigDecimal quantity, Optional<BigDecimal> marketValue,
		Optional<BigDecimal> totalReturnPercent) {

	public boolean isShort() {
		return quantity.signum() < 0;
	}

	public boolean isPriced() {
		return marketValue.isPresent();
	}

}
