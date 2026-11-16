package com.cocos.trading.service.search;

import org.springframework.data.domain.Page;

public record InstrumentMatches(SearchQuery query, Page<InstrumentMatch> matches) {

}
