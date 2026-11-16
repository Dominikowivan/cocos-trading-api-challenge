package com.cocos.trading.controller.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import com.cocos.trading.service.search.InstrumentMatch;
import com.cocos.trading.service.search.InstrumentMatches;

public record InstrumentSearchResponse(String query, int page, int size, long totalElements, int totalPages,
		List<InstrumentMatchResponse> results) {

	public static InstrumentSearchResponse of(InstrumentMatches matches) {
		Page<InstrumentMatch> page = matches.matches();
		return new InstrumentSearchResponse(matches.query().text(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages(),
				page.getContent().stream().map(InstrumentMatchResponse::of).toList());
	}

}
