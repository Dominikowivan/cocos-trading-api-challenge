package com.cocos.trading.model.portfolio;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import com.cocos.trading.model.instrument.InstrumentRef;

public final class Closes {

	private final Map<Long, BigDecimal> byInstrumentId;

	public Closes(Map<Long, BigDecimal> byInstrumentId) {
		this.byInstrumentId = Map.copyOf(byInstrumentId);
	}

	public Optional<BigDecimal> of(InstrumentRef instrument) {
		return Optional.ofNullable(byInstrumentId.get(instrument.instrumentId()));
	}

}
