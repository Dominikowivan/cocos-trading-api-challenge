package com.cocos.trading.model.ledger;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.cocos.trading.model.DecimalPolicy;

public final class AccountSnapshot {

	private final BigDecimal settledCash;

	private final BigDecimal reservedCash;

	private final List<Holding> holdings;

	private final Map<Long, BigDecimal> reservedShares;

	public AccountSnapshot(BigDecimal settledCash, BigDecimal reservedCash, List<Holding> holdings,
			Map<Long, BigDecimal> reservedShares) {
		this.settledCash = settledCash;
		this.reservedCash = reservedCash;
		this.holdings = List.copyOf(holdings);
		this.reservedShares = Map.copyOf(reservedShares);
	}

	public BigDecimal settledCash() {
		return DecimalPolicy.money(settledCash);
	}

	public BigDecimal availableCash() {
		return DecimalPolicy.money(settledCash.subtract(reservedCash));
	}

	public List<Holding> holdings() {
		return holdings;
	}

	public List<Long> heldInstrumentIds() {
		return holdings.stream().map(holding -> holding.instrument().instrumentId()).toList();
	}

	public BigDecimal availableQuantityOf(long instrumentId) {
		return DecimalPolicy.quantity(heldQuantityOf(instrumentId).subtract(reservedSharesOf(instrumentId)));
	}

	private BigDecimal heldQuantityOf(long instrumentId) {
		return holdings.stream()
			.filter(holding -> holding.instrument().instrumentId() == instrumentId)
			.map(Holding::quantity)
			.findFirst()
			.orElse(BigDecimal.ZERO);
	}

	private BigDecimal reservedSharesOf(long instrumentId) {
		return reservedShares.getOrDefault(instrumentId, BigDecimal.ZERO);
	}

}
