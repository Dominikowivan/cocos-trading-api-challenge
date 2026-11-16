package com.cocos.trading.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DecimalPolicy {

	private static final int MONEY_SCALE = 2;

	private static final int RETURN_SCALE = 2;

	private static final int AVERAGE_COST_SCALE = 10;

	private static final int QUANTITY_SCALE = 0;

	private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

	private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

	private DecimalPolicy() {
	}

	public static BigDecimal money(BigDecimal amount) {
		return amount.setScale(MONEY_SCALE, ROUNDING);
	}

	public static BigDecimal quantity(BigDecimal amount) {
		return amount.setScale(QUANTITY_SCALE, ROUNDING);
	}

	public static BigDecimal scaleAverageCost(BigDecimal perShare) {
		return perShare.setScale(AVERAGE_COST_SCALE, ROUNDING);
	}

	public static BigDecimal averageCost(BigDecimal cost, BigDecimal shares) {
		return cost.divide(shares, AVERAGE_COST_SCALE, ROUNDING);
	}

	public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
		return part.multiply(PERCENT).divide(whole, RETURN_SCALE, ROUNDING);
	}

	public static BigDecimal zeroMoney() {
		return money(BigDecimal.ZERO);
	}

}
