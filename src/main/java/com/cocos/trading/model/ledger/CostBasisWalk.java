package com.cocos.trading.model.ledger;

import java.math.BigDecimal;

import com.cocos.trading.model.DecimalPolicy;

public final class CostBasisWalk {

	private BigDecimal quantity = BigDecimal.ZERO;

	private BigDecimal averageCost = BigDecimal.ZERO;

	public void accept(BigDecimal signedQuantity, BigDecimal price) {
		if (quantity.signum() == 0) {
			open(signedQuantity, price);
		}
		else if (quantity.signum() == signedQuantity.signum()) {
			extend(signedQuantity, price);
		}
		else {
			reduce(signedQuantity, price);
		}
	}

	public BigDecimal quantity() {
		return quantity;
	}

	public BigDecimal averageCost() {
		return averageCost;
	}

	private void open(BigDecimal signedQuantity, BigDecimal price) {
		quantity = signedQuantity;
		averageCost = DecimalPolicy.scaleAverageCost(price);
	}

	private void extend(BigDecimal signedQuantity, BigDecimal price) {
		BigDecimal held = quantity.abs();
		BigDecimal incoming = signedQuantity.abs();
		averageCost = DecimalPolicy.averageCost(held.multiply(averageCost).add(incoming.multiply(price)),
				held.add(incoming));
		quantity = quantity.add(signedQuantity);
	}

	private void reduce(BigDecimal signedQuantity, BigDecimal price) {
		BigDecimal remaining = quantity.add(signedQuantity);

		if (remaining.signum() == 0) {
			close();
		}
		else if (remaining.signum() == quantity.signum()) {
			quantity = remaining;
		}
		else {
			crossZero(remaining, price);
		}
	}

	private void close() {
		quantity = BigDecimal.ZERO;
		averageCost = BigDecimal.ZERO;
	}

	private void crossZero(BigDecimal remaining, BigDecimal price) {
		open(remaining, price);
	}

}
