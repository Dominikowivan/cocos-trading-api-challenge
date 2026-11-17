package com.cocos.trading.controller.dto;

import java.math.BigDecimal;

public record OrderSubmissionRequest(Long instrumentId, String type, String side, BigDecimal size, BigDecimal amount,
		BigDecimal price) {

}
