package com.cocos.trading.model.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cocos.trading.model.instrument.InstrumentRef;

public record Order(long orderId, InstrumentRef instrument, LedgerSide side, OrderStatus status, OrderType type,
		BigDecimal quantity, BigDecimal price, LocalDateTime occurredAt) {

	public boolean isSettled() {
		return status.isSettled();
	}

	public boolean movesPosition() {
		return isSettled() && !instrument.isCash();
	}

	public BigDecimal signedQuantity() {
		if (!movesPosition()) {
			return BigDecimal.ZERO;
		}
		return side.positionSign().multiply(quantity);
	}

	public BigDecimal cashEffect() {
		if (!isSettled()) {
			return BigDecimal.ZERO;
		}
		return side.cashSign().multiply(quantity).multiply(price);
	}

	public boolean reservesCash() {
		return isAnOpenLimit() && side == OrderSide.BUY;
	}

	public boolean reservesShares() {
		return isAnOpenLimit() && side == OrderSide.SELL;
	}

	private boolean isAnOpenLimit() {
		return status.isOpen() && type == OrderType.LIMIT;
	}

	public BigDecimal committedAmount() {
		return quantity.multiply(price);
	}

}
