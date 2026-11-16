package com.cocos.trading.service.search;

import java.util.List;

public record InstrumentMatches(SearchQuery query, List<InstrumentMatch> matches, SearchPage page, long totalElements) {

	public int totalPages() {
		return (int) ((totalElements + page.size() - 1) / page.size());
	}

}
