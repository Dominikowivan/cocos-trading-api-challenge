package com.cocos.trading.controller.dto.response;

import java.math.BigDecimal;

import com.cocos.trading.model.instrument.DailyClose;
import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.service.search.InstrumentMatch;

public record InstrumentMatchResponse(long instrumentId, String ticker, String name, String type, BigDecimal close,
		BigDecimal dailyReturnPercent) {

	public static InstrumentMatchResponse of(InstrumentMatch result) {
		InstrumentRef instrument = result.instrument();
		DailyClose dailyClose = result.dailyClose();
		return new InstrumentMatchResponse(instrument.instrumentId(), instrument.ticker(), instrument.name(),
				instrument.type(), dailyClose.close().orElse(null), dailyClose.dailyReturnPercent().orElse(null));
	}

}
