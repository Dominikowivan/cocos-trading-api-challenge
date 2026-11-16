package com.cocos.trading.model.portfolio;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.cocos.trading.model.DecimalPolicy;
import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.ledger.Holding;

public final class Portfolio {

	private static final Comparator<Position> BY_TICKER = Comparator
		.comparing(position -> position.instrument().ticker());

	private final BigDecimal settledCash;

	private final BigDecimal availableCash;

	private final List<Position> positions;

	public Portfolio(BigDecimal settledCash, BigDecimal availableCash, List<Position> positions) {
		this.settledCash = settledCash;
		this.availableCash = availableCash;
		this.positions = positions.stream().sorted(BY_TICKER).toList();
	}

	public static Portfolio valued(AccountSnapshot snapshot, Closes closes) {
		List<Position> positions = snapshot.holdings().stream().map(holding -> positionOf(holding, closes)).toList();

		return new Portfolio(snapshot.settledCash(), snapshot.availableCash(), positions);
	}

	public BigDecimal settledCash() {
		return settledCash;
	}

	public BigDecimal availableCash() {
		return availableCash;
	}

	public List<Position> positions() {
		return positions;
	}

	public List<Position> shortPositions() {
		return positions.stream().filter(Position::isShort).toList();
	}

	public List<Position> unpricedPositions() {
		return positions.stream().filter(position -> !position.isPriced()).toList();
	}

	public Optional<BigDecimal> totalAccountValue() {
		if (!unpricedPositions().isEmpty()) {
			return Optional.empty();
		}

		BigDecimal held = positions.stream()
			.map(position -> position.marketValue().orElseThrow())
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		return Optional.of(DecimalPolicy.money(settledCash.add(held)));
	}

	private static Position positionOf(Holding holding, Closes closes) {
		Optional<BigDecimal> marketValue = closes.of(holding.instrument())
			.map(close -> DecimalPolicy.money(holding.quantity().multiply(close)));
		return new Position(holding.instrument(), holding.quantity(), marketValue,
				marketValue.flatMap(value -> totalReturnPercentOf(holding, value)));
	}

	private static Optional<BigDecimal> totalReturnPercentOf(Holding holding, BigDecimal marketValue) {
		if (holding.isShort() || holding.totalCost().signum() == 0) {
			return Optional.empty();
		}
		return Optional.of(DecimalPolicy.percent(marketValue.subtract(holding.totalCost()), holding.totalCost()));
	}

}
