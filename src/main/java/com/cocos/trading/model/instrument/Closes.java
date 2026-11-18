package com.cocos.trading.model.instrument;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.cocos.trading.model.Price;

public final class Closes {

	private final Map<Long, Price> byInstrumentId;

	public Closes(Map<Long, BigDecimal> byInstrumentId) {
		Map<Long, Price> quoted = new LinkedHashMap<>();
		byInstrumentId
			.forEach((instrumentId, close) -> Price.usable(close).ifPresent(price -> quoted.put(instrumentId, price)));
		this.byInstrumentId = Map.copyOf(quoted);
	}

	public Optional<Price> of(InstrumentRef instrument) {
		return Optional.ofNullable(byInstrumentId.get(instrument.instrumentId()));
	}

}
