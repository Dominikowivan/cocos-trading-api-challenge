package com.cocos.trading.service.search;

public record SearchLimits(int maxQueryLength) {

	public SearchLimits {
		if (maxQueryLength < 1) {
			throw new IllegalArgumentException(
					"The maximum query length must be at least 1, but was " + maxQueryLength);
		}
	}

}
