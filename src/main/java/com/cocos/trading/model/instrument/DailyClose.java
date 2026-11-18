package com.cocos.trading.model.instrument;

import java.math.BigDecimal;
import java.util.Optional;

import com.cocos.trading.model.DecimalPolicy;

public final class DailyClose {

	private final BigDecimal close;

	private final BigDecimal previousClose;

	private DailyClose(BigDecimal close, BigDecimal previousClose) {
		this.close = close;
		this.previousClose = previousClose;
	}

	public static DailyClose of(BigDecimal close, BigDecimal previousClose) {
		return new DailyClose(close, previousClose);
	}

	public static DailyClose unknown() {
		return new DailyClose(null, null);
	}

	public Optional<BigDecimal> close() {
		return Optional.ofNullable(this.close);
	}

	public Optional<BigDecimal> dailyReturnPercent() {
		boolean hasNoPairOfClosesToCompare = this.close == null || isUnusableDivisor();

		if (hasNoPairOfClosesToCompare) {
			return Optional.empty();
		}
		return Optional.of(DecimalPolicy.percent(this.close.subtract(this.previousClose), this.previousClose));
	}

	private boolean isUnusableDivisor() {
		return this.previousClose == null || this.previousClose.compareTo(BigDecimal.ZERO) == 0;
	}

}
