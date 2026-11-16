package com.cocos.trading.service.search;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("trading.search")
public record SearchProperties(@DefaultValue("64") int maxQueryLength, @DefaultValue("20") int defaultPageSize,
		@DefaultValue("100") int maxPageSize) {

	public SearchLimits limits() {
		return new SearchLimits(maxQueryLength, defaultPageSize, maxPageSize);
	}

}
