package com.cocos.trading.service.search;

import org.springframework.data.domain.Pageable;

public record SearchRequest(String query, Pageable pageable) {

}
