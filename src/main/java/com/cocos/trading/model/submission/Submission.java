package com.cocos.trading.model.submission;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Supplier;

import com.cocos.trading.model.DecimalPolicy;
import com.cocos.trading.model.Price;
import com.cocos.trading.model.instrument.Closes;
import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.order.OrderKind;
import com.cocos.trading.model.order.OrderSide;

public final class Submission {

	private final InstrumentRef instrument;

	private final OrderSide side;

	private final OrderKind kind;

	private final TradeQuantity quantity;

	private final Price limitPrice;

	private Submission(InstrumentRef instrument, OrderSide side, OrderKind kind, TradeQuantity quantity,
			Price limitPrice) {
		this.instrument = instrument;
		this.side = side;
		this.kind = kind;
		this.quantity = quantity;
		this.limitPrice = limitPrice;
	}

	public static Submission of(InstrumentRef instrument, OrderSide side, OrderKind kind, TradeQuantity quantity,
			BigDecimal limitPrice) {
		requireATradableInstrument(instrument);
		return new Submission(instrument, side, kind, quantity, priceExactlyWhenItIsALimitOrder(kind, limitPrice));
	}

	public InstrumentRef instrument() {
		return instrument;
	}

	public OrderSide side() {
		return side;
	}

	public OrderKind kind() {
		return kind;
	}

	public Optional<PricedSubmission> priceGovernedBy(Supplier<Closes> market) {
		return governingPrice(market).map(price -> new PricedSubmission(this, price));
	}

	TradeQuantity quantity() {
		return quantity;
	}

	private Optional<Price> governingPrice(Supplier<Closes> market) {
		if (kind == OrderKind.LIMIT) {
			return Optional.of(limitPrice);
		}
		return market.get().of(instrument);
	}

	private static void requireATradableInstrument(InstrumentRef instrument) {
		if (instrument.isCash()) {
			throw new IllegalArgumentException("Instrument " + instrument.instrumentId() + " (" + instrument.ticker()
					+ ") is cash, not a tradable instrument");
		}
	}

	private static Price priceExactlyWhenItIsALimitOrder(OrderKind kind, BigDecimal price) {
		if (kind == OrderKind.MARKET) {
			requireNoPrice(price);
			return null;
		}
		return Price.usable(price)
			.orElseThrow(() -> new IllegalArgumentException(
					"A LIMIT order requires a price " + DecimalPolicy.moneyBounds()));
	}

	private static void requireNoPrice(BigDecimal price) {
		if (price != null) {
			throw new IllegalArgumentException("A MARKET order executes at the latest close and takes no price");
		}
	}

}
