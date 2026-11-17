package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class IdempotentCancellationTest extends CancellationWebTestBase {

	@Test
	void the_same_keyed_cancellation_twice_is_answered_once() throws Exception {
		String first = bodyOf(cancel(SEEDED_USER, PENDING_BUY, "cancel-5"));

		String again = bodyOf(cancel(SEEDED_USER, PENDING_BUY, "cancel-5"));

		assertThat(again).isEqualTo(first);
		assertThat(reportedAs(again, "status")).isEqualTo("CANCELLED");
		assertThat(historyOf(PENDING_BUY)).hasSize(1);
		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo("663000.00");
	}

	@Test
	void the_same_key_for_another_order_is_a_conflict_and_cancels_nothing() throws Exception {
		cancel(SEEDED_USER, PENDING_BUY, "cancel-5").andExpect(status().isOk());

		cancel(SEEDED_USER, OTHER_PENDING_BUY, "cancel-5").andExpect(status().isConflict());

		assertThat(statusOf(OTHER_PENDING_BUY)).isEqualTo("NEW");
		assertThat(historyOf(OTHER_PENDING_BUY)).isEmpty();
	}

	@Test
	void a_refused_cancellation_leaves_its_key_free() throws Exception {
		cancel(SEEDED_USER, UNKNOWN_ORDER, "k-404").andExpect(status().isNotFound());

		cancel(SEEDED_USER, OTHER_PENDING_BUY, "k-404").andExpect(status().isOk());
	}

	@Test
	void keys_are_per_user() throws Exception {
		cancel(SEEDED_USER, PENDING_BUY, "cancel-5").andExpect(status().isOk());

		cancel(NEVER_TRADED_USER, UNKNOWN_ORDER, "cancel-5").andExpect(status().isNotFound());
	}

	@Test
	void a_blank_or_overlong_key_is_a_bad_request_and_writes_nothing() throws Exception {
		long transitions = historyRows();

		cancel(SEEDED_USER, PENDING_BUY, " ").andExpect(status().isBadRequest());
		cancel(SEEDED_USER, PENDING_BUY, "k".repeat(256)).andExpect(status().isBadRequest());

		assertThat(statusOf(PENDING_BUY)).isEqualTo("NEW");
		assertThat(historyRows()).isEqualTo(transitions);
		assertThat(claimedKeys()).isZero();
	}

	@Test
	void without_a_key_a_cancellation_is_simply_executed() throws Exception {
		cancelled(SEEDED_USER, PENDING_BUY);

		assertThat(claimedKeys()).isZero();
		assertThat(statusOf(PENDING_BUY)).isEqualTo("CANCELLED");
	}

}
