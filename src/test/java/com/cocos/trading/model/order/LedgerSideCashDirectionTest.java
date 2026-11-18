package com.cocos.trading.model.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class LedgerSideCashDirectionTest {

	@ParameterizedTest
	@MethodSource
	void cash_leaves_the_account_when_it_buys_or_is_withdrawn(LedgerSide side) {
		assertThat(side.cashSign()).isEqualByComparingTo("-1");
	}

	@ParameterizedTest
	@MethodSource
	void cash_enters_the_account_when_it_sells_or_is_deposited(LedgerSide side) {
		assertThat(side.cashSign()).isEqualByComparingTo("1");
	}

	@ParameterizedTest
	@EnumSource(OrderSide.class)
	void a_buy_increases_a_position_and_a_sale_decreases_it(OrderSide side) {
		assertThat(side.positionSign()).isEqualByComparingTo(side == OrderSide.BUY ? "1" : "-1");
	}

	@ParameterizedTest
	@EnumSource(CashMovement.class)
	void a_cash_movement_moves_no_position_rather_than_refusing_the_question(CashMovement side) {
		assertThat(side.positionSign()).isEqualByComparingTo("0");
	}

	static Stream<LedgerSide> cash_leaves_the_account_when_it_buys_or_is_withdrawn() {
		return Stream.of(OrderSide.BUY, CashMovement.CASH_OUT);
	}

	static Stream<LedgerSide> cash_enters_the_account_when_it_sells_or_is_deposited() {
		return Stream.of(OrderSide.SELL, CashMovement.CASH_IN);
	}

}
