package com.cocos.trading.controller.dto;

import java.math.BigDecimal;
import java.util.List;

import com.cocos.trading.model.portfolio.Portfolio;
import com.cocos.trading.model.user.UserId;

public record PortfolioResponse(long userId, BigDecimal totalAccountValue, BigDecimal availableCash,
		BigDecimal settledCash, List<PositionResponse> positions) {

	public static PortfolioResponse of(UserId userId, Portfolio portfolio) {
		return new PortfolioResponse(userId.value(), portfolio.totalAccountValue().orElse(null),
				portfolio.availableCash(), portfolio.settledCash(),
				portfolio.positions().stream().map(PositionResponse::of).toList());
	}

}
