package com.cocos.trading.model.submission;

import static org.assertj.core.api.Assertions.fail;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.cocos.trading.model.Price;
import com.cocos.trading.model.instrument.Closes;
import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.ledger.AccountLedger;
import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.ledger.Ledgers;
import com.cocos.trading.model.order.OrderType;
import com.cocos.trading.model.order.OrderSide;

public final class Submissions {

	public static final BigDecimal PAMP_CLOSE = new BigDecimal("925.85");

	private Submissions() {
	}

	public static Supplier<Closes> theMarketOn(InstrumentRef instrument, String close) {
		return theMarketOn(instrument, new BigDecimal(close));
	}

	public static Supplier<Closes> theMarketOn(InstrumentRef instrument, BigDecimal close) {
		Closes closes = new Closes(Map.of(instrument.instrumentId(), close));
		return () -> closes;
	}

	/**
	 * This submission at that price, which only the market may hand it. A LIMIT order
	 * ignores the market and prices itself, so the same call serves both kinds.
	 */
	public static PricedSubmission pricedAt(Submission submission, BigDecimal price) {
		return submission.priceGovernedBy(theMarketOn(submission.instrument(), price)).orElseThrow();
	}

	public static Supplier<Closes> anInstrumentThatDoesNotQuote() {
		Closes nothing = new Closes(Map.of());
		return () -> nothing;
	}

	public static Supplier<Closes> aMarketNobodyMayConsult() {
		return () -> fail("a LIMIT order read market data, and FR-011 says it never does");
	}

	/**
	 * The price that governs this submission, which is all the price tests care about.
	 */
	public static Optional<Price> priceGoverning(Submission submission, Supplier<Closes> market) {
		return submission.priceGovernedBy(market).map(PricedSubmission::price);
	}

	public static AccountSnapshot aUserWhoNeverTraded() {
		return new AccountLedger(List.of()).snapshot();
	}

	public static Submission marketBuyOf(long size) {
		return marketOrderOf(OrderSide.BUY, size);
	}

	public static Submission marketSellOf(long size) {
		return marketOrderOf(OrderSide.SELL, size);
	}

	public static Submission marketOrderOf(OrderSide side, long size) {
		return marketOrderOf(Ledgers.PAMP, side, size);
	}

	public static Submission marketOrderOf(InstrumentRef instrument, OrderSide side, long size) {
		return Submission.of(instrument, side, OrderType.MARKET, sizeOf(size), null);
	}

	public static Submission marketOrderOfAmount(OrderSide side, String amount) {
		return marketOrderOfAmount(Ledgers.PAMP, side, amount);
	}

	public static Submission marketOrderOfAmount(InstrumentRef instrument, OrderSide side, String amount) {
		return Submission.of(instrument, side, OrderType.MARKET, amountOf(amount), null);
	}

	public static Submission limitOrderOf(OrderSide side, long size, String price) {
		return limitOrderOf(Ledgers.PAMP, side, size, price);
	}

	public static Submission limitOrderOf(InstrumentRef instrument, OrderSide side, long size, String price) {
		return Submission.of(instrument, side, OrderType.LIMIT, sizeOf(size), new BigDecimal(price));
	}

	public static Submission limitOrderOfAmount(OrderSide side, String amount, String price) {
		return Submission.of(Ledgers.PAMP, side, OrderType.LIMIT, amountOf(amount), new BigDecimal(price));
	}

	public static TradeQuantity sizeOf(long size) {
		return TradeQuantity.ofSize(BigDecimal.valueOf(size));
	}

	public static TradeQuantity amountOf(String amount) {
		return TradeQuantity.ofAmount(new BigDecimal(amount));
	}

}
