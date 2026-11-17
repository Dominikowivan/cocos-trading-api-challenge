package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class ReplayAfterCancellationTest extends CancellationWebTestBase {

	private static final String LIMIT_BUY = limitOrderOf(PAMP, "BUY", sizeOf(10), "900");

	@Test
	void a_submission_replayed_after_its_order_was_cancelled_answers_its_first_outcome() throws Exception {
		String placed = createdBodyOf(submit(SEEDED_USER, LIMIT_BUY, "buy-1"));
		long orderId = Long.parseLong(reportedAs(placed, "orderId"));

		cancel(SEEDED_USER, orderId, "buy-1").andExpect(status().isConflict());
		cancelled(SEEDED_USER, orderId);
		String replayed = createdBodyOf(submit(SEEDED_USER, LIMIT_BUY, "buy-1"));

		assertThat(replayed).isEqualTo(placed);
		assertThat(reportedAs(replayed, "status")).isEqualTo("NEW");
		assertThat(reportedAs(replayed, "rejectionReason")).isEqualTo("null");
	}

	@Test
	void a_rejected_submission_still_replays_rejected_with_its_reason() throws Exception {
		String unaffordable = marketOrderOf(PAMP, "BUY", sizeOf(1000));
		createdBodyOf(submit(SEEDED_USER, unaffordable, "big-1"));

		String replayed = createdBodyOf(submit(SEEDED_USER, unaffordable, "big-1"));

		assertThat(reportedAs(replayed, "status")).isEqualTo("REJECTED");
		assertThat(reportedAs(replayed, "rejectionReason")).isEqualTo("INSUFFICIENT_FUNDS");
	}

	@Test
	void a_rejected_withdrawal_still_replays_rejected_with_its_reason() throws Exception {
		createdBodyOf(transfer(NEVER_TRADED_USER, withdrawalOf(1), "out-1"));

		String replayed = createdBodyOf(transfer(NEVER_TRADED_USER, withdrawalOf(1), "out-1"));

		assertThat(reportedAs(replayed, "status")).isEqualTo("REJECTED");
		assertThat(reportedAs(replayed, "rejectionReason")).isEqualTo("INSUFFICIENT_FUNDS");
	}

}
