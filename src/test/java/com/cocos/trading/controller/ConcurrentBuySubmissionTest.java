package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class ConcurrentBuySubmissionTest extends SubmissionWebTestBase {

	private static final String FOUR_HUNDRED_PAMP = marketOrderOf(PAMP, "BUY", sizeOf(400));

	private static final String ONE_HUNDRED_PAMP = marketOrderOf(PAMP, "BUY", sizeOf(100));

	@RepeatedTest(5)
	void two_purchases_that_cannot_both_be_paid_for_fill_exactly_one() throws Exception {
		long before = ordersInTheLedger();

		List<String> outcomes = outcomesOf(SEEDED_USER, FOUR_HUNDRED_PAMP, SEEDED_USER, FOUR_HUNDRED_PAMP);

		assertThat(statusesIn(outcomes)).containsExactlyInAnyOrder("FILLED", "REJECTED");
		assertThat(rejectionIn(outcomes)).isEqualTo("INSUFFICIENT_FUNDS");
		assertThat(ordersInTheLedger()).isEqualTo(before + 2);
	}

	@Test
	void the_cash_left_is_the_one_the_single_fill_implies() throws Exception {
		outcomesOf(SEEDED_USER, FOUR_HUNDRED_PAMP, SEEDED_USER, FOUR_HUNDRED_PAMP);

		mvc.perform(get(PORTFOLIO, SEEDED_USER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.availableCash").value(257160.00));
	}

	@Test
	void two_purchases_that_both_fit_are_both_filled() throws Exception {
		List<String> outcomes = outcomesOf(SEEDED_USER, ONE_HUNDRED_PAMP, SEEDED_USER, ONE_HUNDRED_PAMP);

		assertThat(statusesIn(outcomes)).containsExactly("FILLED", "FILLED");

		mvc.perform(get(PORTFOLIO, SEEDED_USER)).andExpect(jsonPath("$.availableCash").value(442330.00));
	}

}
