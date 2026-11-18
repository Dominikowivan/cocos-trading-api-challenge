package com.cocos.trading.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cocos.trading.controller.dto.response.InstrumentSearchResponse;
import com.cocos.trading.service.search.InstrumentSearchService;
import com.cocos.trading.service.search.SearchRequest;

@RestController
@RequestMapping("/api/v1/instruments/search")
class InstrumentSearchController {

	private final InstrumentSearchService instrumentSearchService;

	InstrumentSearchController(InstrumentSearchService instrumentSearchService) {
		this.instrumentSearchService = instrumentSearchService;
	}

	@GetMapping
	InstrumentSearchResponse search(@RequestParam String query, Pageable pageable) {
		return InstrumentSearchResponse.of(instrumentSearchService.search(new SearchRequest(query, pageable)));
	}

}
