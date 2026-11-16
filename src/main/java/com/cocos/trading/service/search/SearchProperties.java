package com.cocos.trading.service.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trading.search")
public record SearchProperties(int maxQueryLength) {

	public SearchLimits limits() {
		return new SearchLimits(maxQueryLength);
	}

}
