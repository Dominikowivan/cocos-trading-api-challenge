package com.cocos.trading.model.submission;

import java.math.BigDecimal;
import java.util.Optional;

import com.cocos.trading.model.DecimalPolicy;
import com.cocos.trading.model.Price;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.order.RejectionReason;

public final class OrderDecision {

	private final OrderStatus status;

	private final BigDecimal size;

	private final Price price;

	private final RejectionReason rejectionReason;

	private OrderDecision(OrderStatus status, BigDecimal size, Price price, RejectionReason rejectionReason) {
		this.status = status;
		this.size = DecimalPolicy.quantity(size);
		this.price = price;
		this.rejectionReason = rejectionReason;
	}

	public static OrderDecision accepted(OrderStatus status, BigDecimal size, Price price) {
		return new OrderDecision(status, size, price, null);
	}

	public static OrderDecision rejected(RejectionReason rejectionReason, BigDecimal size, Price price) {
		return new OrderDecision(OrderStatus.REJECTED, size, price, rejectionReason);
	}

	public OrderStatus status() {
		return status;
	}

	public BigDecimal size() {
		return size;
	}

	public Price price() {
		return price;
	}

	public Optional<RejectionReason> rejectionReason() {
		return Optional.ofNullable(rejectionReason);
	}

}
