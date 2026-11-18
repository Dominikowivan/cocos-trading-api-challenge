package com.cocos.trading.controller.dto.request;

import java.math.BigDecimal;

public record OrderSubmissionRequest(Long instrumentId, String type, String side, BigDecimal size, BigDecimal amount,
		BigDecimal price) {

}
