package com.cocos.trading.model.ledger;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.order.CashMovement;
import com.cocos.trading.model.order.LedgerSide;
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

	public static AccountLedger seededUserOne() {
		return new AccountLedger(orders(cashIn(1000000, "2023-07-12T12:11:20"),
				filledBuy(PAMP, 50, "930", "2023-07-12T12:31:20"),
				cancelledBuyLimit(PAMP, 50, "920", "2023-07-12T14:21:20"),
				filledSell(PAMP, 10, "940", "2023-07-12T14:51:20"),
				openBuyLimit(LOMA, 50, "710", "2023-07-12T15:14:20"),
				rejectedSell(PAMP, 100, "950", "2023-07-12T16:11:20"),
				openBuyLimit(BMA, 60, "1500", "2023-07-13T11:13:20"), cashOut(100000, "2023-07-13T12:31:20"),
				filledBuy(BMA, 20, "1540", "2023-07-13T12:51:20"), filledBuy(METR, 500, "250", "2023-07-13T14:11:20"),
				filledSell(BMA, 30, "1530", "2023-07-13T15:13:20")));
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
		return order(ARS, CashMovement.CASH_IN, OrderStatus.FILLED, OrderKind.MARKET, amount, "1", at);
	}

	public static Order cashOut(long amount, String at) {
		return order(ARS, CashMovement.CASH_OUT, OrderStatus.FILLED, OrderKind.MARKET, amount, "1", at);
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

	public static Order order(InstrumentRef instrument, LedgerSide side, OrderStatus status, OrderKind kind, long size,
			String price, String at) {
		return new Order(IDS.incrementAndGet(), instrument, side, status, kind, BigDecimal.valueOf(size),
				new BigDecimal(price), LocalDateTime.parse(at));
	}

}
