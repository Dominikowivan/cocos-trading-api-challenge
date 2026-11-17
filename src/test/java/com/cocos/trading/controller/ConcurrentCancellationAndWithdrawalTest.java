package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.RepeatedTest;

class ConcurrentCancellationAndWithdrawalTest extends CancellationWebTestBase {

	private static final long AVAILABLE_ONCE_CANCELLED = 663000;

	@RepeatedTest(5)
	void a_withdrawal_sees_the_release_either_entirely_or_not_at_all() throws Exception {
		List<String> outcomes = Simultaneously.theseTwo(() -> cancelled(SEEDED_USER, PENDING_BUY),
				() -> withdrawn(SEEDED_USER, AVAILABLE_ONCE_CANCELLED));

		String withdrawal = outcomes.get(1);
		BigDecimal available = availableCashOf(SEEDED_USER);
		assertThat(reportedAs(outcomes.get(0), "status")).isEqualTo("CANCELLED");
		if ("FILLED".equals(reportedAs(withdrawal, "status"))) {
			assertThat(available).isEqualByComparingTo("0.00");
		}
		else {
			assertThat(reportedAs(withdrawal, "rejectionReason")).isEqualTo("INSUFFICIENT_FUNDS");
			assertThat(available).isEqualByComparingTo("663000.00");
		}
		assertThat(available).isNotNegative();
	}

}
