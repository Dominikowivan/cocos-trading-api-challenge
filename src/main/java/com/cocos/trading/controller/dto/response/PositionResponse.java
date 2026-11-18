package com.cocos.trading.controller.dto.response;

import java.math.BigDecimal;

import com.cocos.trading.model.portfolio.Position;

public record PositionResponse(long instrumentId, String ticker, String name, BigDecimal quantity,
		BigDecimal marketValue, BigDecimal totalReturnPercent) {

	public static PositionResponse of(Position position) {
		return new PositionResponse(position.instrument().instrumentId(), position.instrument().ticker(),
				position.instrument().name(), position.quantity(), position.marketValue().orElse(null),
				position.totalReturnPercent().orElse(null));
	}

}
