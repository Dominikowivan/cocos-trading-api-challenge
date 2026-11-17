package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.RepeatedTest;

class ConcurrentIdempotentSubmissionTest extends SubmissionWebTestBase {

	private static final String A_KEY = "b1d9b1f4-0f1a-4f1e-8a1f-2f6c3c9d0e7b";

	private static final String ONE_HUNDRED_PAMP = marketOrderOf(PAMP, "BUY", sizeOf(100));

	@RepeatedTest(5)
	void two_requests_with_the_same_key_at_the_same_instant_yield_one_order() throws Exception {
		long before = ordersInTheLedger();

		List<String> outcomes = Simultaneously.theseTwo(
				() -> createdBodyOf(submit(SEEDED_USER, ONE_HUNDRED_PAMP, A_KEY)),
				() -> createdBodyOf(submit(SEEDED_USER, ONE_HUNDRED_PAMP, A_KEY)));

		assertThat(statusesIn(outcomes)).containsExactly("FILLED", "FILLED");
		assertThat(reportedAs(outcomes.get(0), "orderId")).isEqualTo(reportedAs(outcomes.get(1), "orderId"));
		assertThat(ordersInTheLedger()).isEqualTo(before + 1);
	}

	@RepeatedTest(3)
	void the_loser_returns_the_committed_outcome_of_a_rejection_too() throws Exception {
		String unaffordable = marketOrderOf(PAMP, "BUY", sizeOf(1000));
		long before = ordersInTheLedger();

		List<String> outcomes = Simultaneously.theseTwo(() -> createdBodyOf(submit(SEEDED_USER, unaffordable, A_KEY)),
				() -> createdBodyOf(submit(SEEDED_USER, unaffordable, A_KEY)));

		assertThat(statusesIn(outcomes)).containsExactly("REJECTED", "REJECTED");
		assertThat(reportedAs(outcomes.get(0), "orderId")).isEqualTo(reportedAs(outcomes.get(1), "orderId"));
		assertThat(ordersInTheLedger()).isEqualTo(before + 1);
	}

}
