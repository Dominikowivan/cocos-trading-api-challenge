package com.cocos.trading.model.ledger;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.order.Order;
import com.cocos.trading.model.order.OrderKind;
import com.cocos.trading.model.order.OrderSide;
import com.cocos.trading.model.order.OrderStatus;

public final class Ledgers {

	public static final InstrumentRef ARS = new InstrumentRef(66, "ARS", "PESOS", "MONEDA");

	public static final InstrumentRef BMA = new InstrumentRef(31, "BMA", "Banco Macro S.A.", "ACCIONES");

	public static final InstrumentRef LOMA = new InstrumentRef(45, "LOMA", "Loma Negra S.A.", "ACCIONES");

	public static final InstrumentRef METR = new InstrumentRef(54, "METR", "MetroGAS S.A.", "ACCIONES");

	public static final InstrumentRef PAMP = new InstrumentRef(47, "PAMP", "Pampa Holding S.A.", "ACCIONES");

	public static final InstrumentRef PGR = new InstrumentRef(3, "PGR", "Phoenix Global Resources", "ACCIONES");

	private static final AtomicLong IDS = new AtomicLong();

	private Ledgers() {
	}

	public static List<Order> orders(Order... orders) {
		return List.of(orders);
	}

	public static Order filledBuy(InstrumentRef instrument, long size, String price, String at) {
		return order(instrument, OrderSide.BUY, OrderStatus.FILLED, OrderKind.MARKET, size, price, at);
	}

	public static Order filledSell(InstrumentRef instrument, long size, String price, String at) {
		return order(instrument, OrderSide.SELL, OrderStatus.FILLED, OrderKind.MARKET, size, price, at);
	}

	public static Order cashIn(long amount, String at) {
		return order(ARS, OrderSide.CASH_IN, OrderStatus.FILLED, OrderKind.MARKET, amount, "1", at);
	}

	public static Order cashOut(long amount, String at) {
		return order(ARS, OrderSide.CASH_OUT, OrderStatus.FILLED, OrderKind.MARKET, amount, "1", at);
	}

	public static Order openBuyLimit(InstrumentRef instrument, long size, String price, String at) {
		return order(instrument, OrderSide.BUY, OrderStatus.NEW, OrderKind.LIMIT, size, price, at);
	}

	public static Order openSellLimit(InstrumentRef instrument, long size, String price, String at) {
		return order(instrument, OrderSide.SELL, OrderStatus.NEW, OrderKind.LIMIT, size, price, at);
	}

	public static Order openBuyMarket(InstrumentRef instrument, long size, String price, String at) {
		return order(instrument, OrderSide.BUY, OrderStatus.NEW, OrderKind.MARKET, size, price, at);
	}

	public static Order cancelledBuyLimit(InstrumentRef instrument, long size, String price, String at) {
		return order(instrument, OrderSide.BUY, OrderStatus.CANCELLED, OrderKind.LIMIT, size, price, at);
	}

	public static Order rejectedSell(InstrumentRef instrument, long size, String price, String at) {
		return order(instrument, OrderSide.SELL, OrderStatus.REJECTED, OrderKind.MARKET, size, price, at);
	}

	public static Order order(InstrumentRef instrument, OrderSide side, OrderStatus status, OrderKind kind,
			long size, String price, String at) {
		return new Order(IDS.incrementAndGet(), instrument, side, status, kind, BigDecimal.valueOf(size),
				new BigDecimal(price), LocalDateTime.parse(at));
	}

}
