package com.cocos.trading.controller.dto;

import java.util.List;

import com.cocos.trading.service.search.InstrumentMatches;

public record InstrumentSearchResponse(String query, int page, int size, long totalElements, int totalPages,
		List<InstrumentMatchResponse> results) {

	public static InstrumentSearchResponse of(InstrumentMatches matches) {
		return new InstrumentSearchResponse(matches.query().text(), matches.page().number(), matches.page().size(),
				matches.totalElements(), matches.totalPages(),
				matches.matches().stream().map(InstrumentMatchResponse::of).toList());
	}

}
