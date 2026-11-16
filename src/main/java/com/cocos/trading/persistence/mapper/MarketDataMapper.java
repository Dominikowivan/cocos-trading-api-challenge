package com.cocos.trading.persistence.mapper;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cocos.trading.model.portfolio.Closes;
import com.cocos.trading.persistence.LatestClose;

public final class MarketDataMapper {

	private MarketDataMapper() {
	}

	public static List<Integer> toInstrumentIdColumns(Collection<Long> instrumentIds) {
		return instrumentIds.stream().map(Long::intValue).toList();
	}

	public static Closes toCloses(List<LatestClose> latestCloses) {
		Map<Long, BigDecimal> closes = new LinkedHashMap<>();

		for (LatestClose latest : latestCloses) {
			if (latest.getClose() != null) {
				closes.put(latest.getInstrumentId().longValue(), latest.getClose());
			}
		}

		return new Closes(closes);
	}

}
