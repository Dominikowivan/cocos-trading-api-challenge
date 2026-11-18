package com.cocos.trading.model.ledger;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.order.Order;

public final class AccountLedger {

	private final List<Order> orders;

	public AccountLedger(List<Order> orders) {
		this.orders = List.copyOf(orders);
	}

	public AccountSnapshot snapshot() {
		return new AccountSnapshot(settledCash(), reservedCash(), holdings(), reservedShares());
	}

	private BigDecimal settledCash() {
		return orders.stream().map(Order::cashEffect).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private BigDecimal reservedCash() {
		return orders.stream()
			.filter(Order::reservesCash)
			.map(Order::committedAmount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private List<Holding> holdings() {
		return orders.stream()
			.filter(Order::movesPosition)
			.collect(Collectors.groupingBy(Order::instrument, LinkedHashMap::new, Collectors.toList()))
			.entrySet()
			.stream()
			.map(byInstrument -> holdingOf(byInstrument.getKey(), byInstrument.getValue()))
			.filter(holding -> !holding.isFlat())
			.toList();
	}

	private Map<Long, BigDecimal> reservedShares() {
		return orders.stream()
			.filter(Order::reservesShares)
			.collect(Collectors.groupingBy(order -> order.instrument().instrumentId(), LinkedHashMap::new,
					Collectors.reducing(BigDecimal.ZERO, Order::quantity, BigDecimal::add)));
	}

	private static Holding holdingOf(InstrumentRef instrument, List<Order> orders) {
		CostBasisWalk walk = new CostBasisWalk();
		orders.forEach(order -> walk.accept(order.signedQuantity(), order.price()));
		return new Holding(instrument, walk.quantity(), walk.averageCost());
	}

}
