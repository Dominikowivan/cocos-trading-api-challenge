package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class ConcurrentSellSubmissionTest extends SubmissionWebTestBase {

	private static final String THE_WHOLE_PAMP_POSITION = marketOrderOf(PAMP, "SELL", sizeOf(40));

	private static final String HALF_THE_PAMP_POSITION = marketOrderOf(PAMP, "SELL", sizeOf(20));

	@RepeatedTest(5)
	void two_sales_of_the_whole_position_fill_exactly_one() throws Exception {
		List<String> outcomes = outcomesOf(SEEDED_USER, THE_WHOLE_PAMP_POSITION, SEEDED_USER, THE_WHOLE_PAMP_POSITION);

		assertThat(statusesIn(outcomes)).containsExactlyInAnyOrder("FILLED", "REJECTED");
		assertThat(rejectionIn(outcomes)).isEqualTo("INSUFFICIENT_HOLDINGS");
	}

	@RepeatedTest(5)
	void these_writes_never_drive_the_position_negative() throws Exception {
		outcomesOf(SEEDED_USER, THE_WHOLE_PAMP_POSITION, SEEDED_USER, THE_WHOLE_PAMP_POSITION);

		mvc.perform(get(PORTFOLIO, SEEDED_USER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.positions[?(@.ticker == 'PAMP')]").isEmpty())
			.andExpect(jsonPath("$.availableCash").value(664534.00));
	}

	@Test
	void two_sales_that_both_fit_are_both_filled() throws Exception {
		List<String> outcomes = outcomesOf(SEEDED_USER, HALF_THE_PAMP_POSITION, SEEDED_USER, HALF_THE_PAMP_POSITION);

		assertThat(statusesIn(outcomes)).containsExactly("FILLED", "FILLED");
	}

	@Test
	void two_users_submitting_at_once_are_both_served() throws Exception {
		fund(NEVER_TRADED_USER, 500000);

		List<String> outcomes = outcomesOf(SEEDED_USER, marketOrderOf(PAMP, "BUY", sizeOf(400)), NEVER_TRADED_USER,
				marketOrderOf(PAMP, "BUY", sizeOf(400)));

		assertThat(statusesIn(outcomes)).containsExactly("FILLED", "FILLED");
	}

	@Test
	void a_submission_does_not_wait_on_another_users_lock() throws Exception {
		try (Connection otherUsersLock = lockedUser(SEEDED_USER)) {
			submit(NEVER_TRADED_USER, marketOrderOf(PAMP, "BUY", sizeOf(1))).andExpect(status().isCreated());

			otherUsersLock.rollback();
		}
	}

}
