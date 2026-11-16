package com.cocos.trading.model.portfolio;

import static com.cocos.trading.model.ledger.Ledgers.BMA;
import static com.cocos.trading.model.ledger.Ledgers.METR;
import static com.cocos.trading.model.ledger.Ledgers.PAMP;
import static com.cocos.trading.model.ledger.Ledgers.PGR;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.instrument.InstrumentRef;

class PortfolioTotalValueTest {

	@Test
	void the_total_is_settled_cash_plus_every_position_value() {
		Portfolio portfolio = new Portfolio(money("753000.00"), money("627500.00"), List.of(
				position(PAMP, 40, "37034.00"),
				position(METR, 500, "114750.00"),
				position(BMA, -10, "-15028.00")));

		assertThat(portfolio.totalAccountValue().orElseThrow()).isEqualByComparingTo("889756.00");
	}

	@Test
	void a_negative_position_lowers_the_total() {
		Portfolio withShort = new Portfolio(money("753000.00"), money("627500.00"),
				List.of(position(BMA, -10, "-15028.00")));
		Portfolio withoutShort = new Portfolio(money("753000.00"), money("627500.00"), List.of());

		assertThat(withShort.totalAccountValue().orElseThrow())
			.isLessThan(withoutShort.totalAccountValue().orElseThrow());
	}

	@Test
	void the_total_counts_reserved_pesos_because_they_are_still_the_users_money() {
		Portfolio portfolio = new Portfolio(money("753000.00"), money("627500.00"), List.of(
				position(PAMP, 40, "37034.00"),
				position(METR, 500, "114750.00"),
				position(BMA, -10, "-15028.00")));

		BigDecimal onAvailableCash = portfolio.availableCash().add(new BigDecimal("136756.00"));

		assertThat(portfolio.totalAccountValue().orElseThrow()).isGreaterThan(onAvailableCash);
		assertThat(portfolio.totalAccountValue().orElseThrow().subtract(onAvailableCash))
			.isEqualByComparingTo("125500.00");
	}

	@Test
	void a_user_with_no_orders_reports_zero_rather_than_absent() {
		Portfolio portfolio = new Portfolio(money("0.00"), money("0.00"), List.of());

		assertThat(portfolio.totalAccountValue().orElseThrow()).isEqualByComparingTo("0.00");
	}

	@Test
	void one_unpriced_position_suppresses_the_total_rather_than_yielding_a_partial_sum() {
		Portfolio portfolio = new Portfolio(money("477500.00"), money("477500.00"), List.of(
				position(PAMP, 40, "37034.00"),
				unpriced(PGR, 25)));

		assertThat(portfolio.totalAccountValue()).isEmpty();
	}

	private static BigDecimal money(String amount) {
		return new BigDecimal(amount);
	}

	private static Position position(InstrumentRef instrument, long quantity, String marketValue) {
		return new Position(instrument, BigDecimal.valueOf(quantity), Optional.of(money(marketValue)),
				Optional.empty());
	}

	private static Position unpriced(InstrumentRef instrument, long quantity) {
		return new Position(instrument, BigDecimal.valueOf(quantity), Optional.empty(), Optional.empty());
	}

}
