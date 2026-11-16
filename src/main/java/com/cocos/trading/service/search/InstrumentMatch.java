package com.cocos.trading.service.search;

import java.util.List;

import com.cocos.trading.model.instrument.DailyClose;
import com.cocos.trading.model.instrument.DailyCloses;
import com.cocos.trading.model.instrument.InstrumentRef;

public record InstrumentMatch(InstrumentRef instrument, DailyClose dailyClose) {

	public static List<InstrumentMatch> priced(List<InstrumentRef> instruments, DailyCloses dailyCloses) {
		return instruments.stream()
			.map(instrument -> new InstrumentMatch(instrument, dailyCloses.of(instrument)))
			.toList();
	}

}
