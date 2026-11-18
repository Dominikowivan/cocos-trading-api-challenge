package com.cocos.trading.model.portfolio;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.cocos.trading.model.instrument.Closes;
import com.cocos.trading.model.DecimalPolicy;
import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.ledger.Holding;

public record Portfolio(BigDecimal settledCash, BigDecimal availableCash, List<Position> positions) {

	private static final Comparator<Position> BY_TICKER = Comparator
		.comparing(position -> position.instrument().ticker());

	public Portfolio {
		positions = positions.stream().sorted(BY_TICKER).toList();
	}

	public static Portfolio valued(AccountSnapshot snapshot, Closes closes) {
		List<Position> positions = snapshot.holdings().stream().map(holding -> positionOf(holding, closes)).toList();

		return new Portfolio(snapshot.settledCash(), snapshot.availableCash(), positions);
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
			.map(close -> DecimalPolicy.money(close.times(holding.quantity())));
		return new Position(holding.instrument(), holding.quantity(), marketValue,
				marketValue.flatMap(value -> totalReturnPercentOf(holding, value)));
	}

	private static Optional<BigDecimal> totalReturnPercentOf(Holding holding, BigDecimal marketValue) {
		boolean hasNoCostBasisToCompareAgainst = holding.isShort() || holding.totalCost().signum() == 0;

		if (hasNoCostBasisToCompareAgainst) {
			return Optional.empty();
		}
		return Optional.of(DecimalPolicy.percent(marketValue.subtract(holding.totalCost()), holding.totalCost()));
	}

}
