package com.cocos.trading.model.instrument;

import java.util.Map;

public final class DailyCloses {

	private final Map<Long, DailyClose> byInstrumentId;

	public DailyCloses(Map<Long, DailyClose> byInstrumentId) {
		this.byInstrumentId = Map.copyOf(byInstrumentId);
	}

	public DailyClose of(InstrumentRef instrument) {
		return byInstrumentId.getOrDefault(instrument.instrumentId(), DailyClose.unknown());
	}

}
