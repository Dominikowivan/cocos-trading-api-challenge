package com.cocos.trading.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;


public final class Price {

	private final BigDecimal amount;

	private Price(BigDecimal amount) {
		this.amount = amount;
	}


	public static Price of(BigDecimal amount) {
		return usable(amount).orElseThrow(() -> new IllegalArgumentException(
				"A price is " + DecimalPolicy.moneyBounds() + ", and " + stated(amount) + " is not"));
	}


	public static Optional<Price> usable(BigDecimal amount) {
		if (amount == null) {
			return Optional.empty();
		}
		BigDecimal scaled = DecimalPolicy.money(amount);
		return DecimalPolicy.fitsTheMoneyColumn(scaled) ? Optional.of(new Price(scaled)) : Optional.empty();
	}

	public BigDecimal amount() {
		return amount;
	}


	public BigDecimal times(BigDecimal quantity) {
		return quantity.multiply(amount);
	}


	public BigDecimal wholeSharesIn(BigDecimal money) {
		return DecimalPolicy.wholeSharesIn(money, amount);
	}

	private static String stated(BigDecimal amount) {
		return amount == null ? "no amount at all" : amount.toPlainString();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Price price && amount.equals(price.amount);
	}

	@Override
	public int hashCode() {
		return Objects.hash(amount);
	}

	@Override
	public String toString() {
		return amount.toPlainString();
	}

}
