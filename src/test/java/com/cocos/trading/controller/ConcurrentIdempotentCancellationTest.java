package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.RepeatedTest;

class ConcurrentIdempotentCancellationTest extends CancellationWebTestBase {

	@RepeatedTest(5)
	void two_cancellations_with_the_same_key_are_both_answered_with_the_one_cancellation() {
		List<String> answers = Simultaneously.theseTwo(() -> bodyOf(cancel(SEEDED_USER, PENDING_BUY, "cancel-5")),
				() -> bodyOf(cancel(SEEDED_USER, PENDING_BUY, "cancel-5")));

		assertThat(answers).hasSize(2).allSatisfy(answer -> {
			assertThat(reportedAs(answer, "orderId")).isEqualTo("5");
			assertThat(reportedAs(answer, "status")).isEqualTo("CANCELLED");
		});
		assertThat(historyOf(PENDING_BUY)).hasSize(1);
	}

}
