package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.RepeatedTest;

class ConcurrentIdempotentCashTransferTest extends CashTransferWebTestBase {

	private static final String A_KEY = "dep-concurrent";

	@RepeatedTest(5)
	void two_deposits_with_the_same_key_at_the_same_instant_yield_one_order() throws Exception {
		long before = ordersInTheLedger();

		List<String> outcomes = Simultaneously.theseTwo(
				() -> createdBodyOf(transfer(NEVER_TRADED_USER, depositOf(100000), A_KEY)),
				() -> createdBodyOf(transfer(NEVER_TRADED_USER, depositOf(100000), A_KEY)));

		assertThat(statusesIn(outcomes)).containsExactly("FILLED", "FILLED");
		assertThat(reportedAs(outcomes.get(0), "orderId")).isEqualTo(reportedAs(outcomes.get(1), "orderId"));
		assertThat(ordersInTheLedger()).isEqualTo(before + 1);
		assertThat(availableCashOf(NEVER_TRADED_USER)).isEqualByComparingTo("100000.00");
	}

}
