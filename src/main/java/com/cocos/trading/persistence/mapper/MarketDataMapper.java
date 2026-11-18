package com.cocos.trading.persistence.mapper;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cocos.trading.model.instrument.DailyClose;
import com.cocos.trading.model.instrument.DailyCloses;
import com.cocos.trading.model.instrument.Closes;
import com.cocos.trading.persistence.LatestMarketData;

public final class MarketDataMapper {

	private MarketDataMapper() {
	}

	public static List<Integer> toInstrumentIdColumns(Collection<Long> instrumentIds) {
		return instrumentIds.stream().map(Long::intValue).toList();
	}

	public static Closes toCloses(List<LatestMarketData> latestMarketData) {
		Map<Long, BigDecimal> closes = new LinkedHashMap<>();

		for (LatestMarketData latest : latestMarketData) {
			if (latest.getClose() != null) {
				closes.put(latest.getInstrumentId().longValue(), latest.getClose());
			}
		}

		return new Closes(closes);
	}

	public static DailyCloses toDailyCloses(List<LatestMarketData> latestMarketData) {
		Map<Long, DailyClose> byInstrumentId = latestMarketData.stream()
			.collect(Collectors.toMap(latest -> latest.getInstrumentId().longValue(),
					latest -> DailyClose.of(latest.getClose(), latest.getPreviousClose())));

		return new DailyCloses(byInstrumentId);
	}

}
