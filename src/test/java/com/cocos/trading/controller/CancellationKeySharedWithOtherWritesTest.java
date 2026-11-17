package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class CancellationKeySharedWithOtherWritesTest extends CancellationWebTestBase {

	@Test
	void a_key_used_by_a_submission_cannot_cancel() throws Exception {
		createdBodyOf(submit(SEEDED_USER, marketOrderOf(PAMP, "BUY", sizeOf(1)), "k-1"));

		cancel(SEEDED_USER, PENDING_BUY, "k-1").andExpect(status().isConflict());

		assertThat(statusOf(PENDING_BUY)).isEqualTo("NEW");
	}

	@Test
	void a_key_used_by_a_transfer_cannot_cancel() throws Exception {
		createdBodyOf(transfer(NEVER_TRADED_USER, depositOf(100), "t-1"));

		cancel(NEVER_TRADED_USER, UNKNOWN_ORDER, "t-1").andExpect(status().isConflict());
	}

	@Test
	void a_key_used_by_a_cancellation_cannot_submit() throws Exception {
		cancel(SEEDED_USER, PENDING_BUY, "c-1").andExpect(status().isOk());
		long orders = ordersInTheLedger();

		submit(SEEDED_USER, marketOrderOf(PAMP, "BUY", sizeOf(1)), "c-1").andExpect(status().isConflict());

		assertThat(ordersInTheLedger()).isEqualTo(orders);
	}

}
