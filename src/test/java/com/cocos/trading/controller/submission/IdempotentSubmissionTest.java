package com.cocos.trading.controller.submission;

import static com.cocos.trading.controller.ApiPaths.PORTFOLIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.cocos.trading.controller.SubmissionWebTestBase;

class IdempotentSubmissionTest extends SubmissionWebTestBase {

	private static final String A_KEY = "7c9e6679-7425-40de-944b-e07fc1f90ae7";

	private static final String ONE_HUNDRED_PAMP = marketOrderOf(PAMP, "BUY", sizeOf(100));

	private static final long UNKNOWN_USER = 99999;

	@Test
	void the_identical_retry_returns_the_first_order_and_trades_once() throws Exception {
		long before = ordersInTheLedger();

		String first = createdBodyOf(submit(SEEDED_USER, ONE_HUNDRED_PAMP, A_KEY));
		String retry = createdBodyOf(submit(SEEDED_USER, ONE_HUNDRED_PAMP, A_KEY));

		assertThat(reportedAs(retry, "orderId")).isEqualTo(reportedAs(first, "orderId"));
		assertThat(reportedAs(retry, "status")).isEqualTo("FILLED");
		assertThat(ordersInTheLedger()).isEqualTo(before + 1);
	}

	@Test
	void the_retry_moves_the_money_once() throws Exception {
		submit(SEEDED_USER, ONE_HUNDRED_PAMP, A_KEY).andExpect(status().isCreated());
		submit(SEEDED_USER, ONE_HUNDRED_PAMP, A_KEY).andExpect(status().isCreated());

		mvc.perform(get(PORTFOLIO, SEEDED_USER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.availableCash").value(534915.00));
	}

	@Test
	void the_same_request_written_differently_is_the_same_request() throws Exception {
		String spacedOutAndReordered = "{  \"side\" : \"BUY\", \"type\":\"MARKET\",   \"size\": 100.00,"
				+ " \"instrumentId\" : 47 }";
		long before = ordersInTheLedger();

		String first = createdBodyOf(submit(SEEDED_USER, ONE_HUNDRED_PAMP, A_KEY));
		String retry = createdBodyOf(submit(SEEDED_USER, spacedOutAndReordered, A_KEY));

		assertThat(reportedAs(retry, "orderId")).isEqualTo(reportedAs(first, "orderId"));
		assertThat(ordersInTheLedger()).isEqualTo(before + 1);
	}

	@Test
	void the_same_key_with_a_different_body_is_a_conflict_that_writes_nothing() throws Exception {
		submit(SEEDED_USER, ONE_HUNDRED_PAMP, A_KEY).andExpect(status().isCreated());

		refused(SEEDED_USER, marketOrderOf(PAMP, "BUY", sizeOf(7)), A_KEY).andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.detail").value(containsString(A_KEY)));
	}

	@Test
	void a_rejected_outcome_is_replayed_and_not_decided_again() throws Exception {
		String unaffordable = marketOrderOf(PAMP, "BUY", sizeOf(1000));

		String first = createdBodyOf(submit(SEEDED_USER, unaffordable, A_KEY));
		String retry = createdBodyOf(submit(SEEDED_USER, unaffordable, A_KEY));

		assertThat(reportedAs(first, "status")).isEqualTo("REJECTED");
		assertThat(reportedAs(retry, "status")).isEqualTo("REJECTED");
		assertThat(reportedAs(retry, "rejectionReason")).isEqualTo("INSUFFICIENT_FUNDS");
		assertThat(reportedAs(retry, "orderId")).isEqualTo(reportedAs(first, "orderId"));
	}

	@Test
	void a_replayed_limit_order_reports_what_was_recorded() throws Exception {
		String limitBuy = limitOrderOf(PAMP, "BUY", sizeOf(100), "900.00");

		String first = createdBodyOf(submit(SEEDED_USER, limitBuy, A_KEY));
		String retry = createdBodyOf(submit(SEEDED_USER, limitBuy, A_KEY));

		assertThat(reportedAs(retry, "status")).isEqualTo("NEW");
		assertThat(reportedAs(retry, "price")).isEqualTo(reportedAs(first, "price"));
		assertThat(reportedAs(retry, "size")).isEqualTo(reportedAs(first, "size"));
		assertThat(reportedAs(retry, "submittedAt")).isEqualTo(reportedAs(first, "submittedAt"));
		assertThat(reportedAs(retry, "ticker")).isEqualTo("PAMP");
	}

	@Test
	void the_same_key_sent_by_another_user_is_a_new_request() throws Exception {
		submit(SEEDED_USER, ONE_HUNDRED_PAMP, A_KEY).andExpect(status().isCreated());

		submit(NEVER_TRADED_USER, marketOrderOf(PAMP, "BUY", sizeOf(1)), A_KEY).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.userId").value(NEVER_TRADED_USER));
	}

	@Test
	void a_replay_answers_without_ever_taking_the_users_lock() throws Exception {
		String first = createdBodyOf(submit(SEEDED_USER, ONE_HUNDRED_PAMP, A_KEY));

		try (Connection heldByAnotherTransaction = lockedUser(SEEDED_USER)) {
			String retry = createdBodyOf(submit(SEEDED_USER, ONE_HUNDRED_PAMP, A_KEY));

			assertThat(reportedAs(retry, "orderId")).isEqualTo(reportedAs(first, "orderId"));
			assertThat(reportedAs(retry, "status")).isEqualTo("FILLED");
			heldByAnotherTransaction.rollback();
		}
	}

	@Test
	void a_conflict_is_answered_without_ever_taking_the_users_lock() throws Exception {
		submit(SEEDED_USER, ONE_HUNDRED_PAMP, A_KEY).andExpect(status().isCreated());

		try (Connection heldByAnotherTransaction = lockedUser(SEEDED_USER)) {
			refused(SEEDED_USER, marketOrderOf(PAMP, "BUY", sizeOf(7)), A_KEY).andExpect(status().isConflict());

			heldByAnotherTransaction.rollback();
		}
	}

	@Test
	void an_unknown_user_sending_a_key_is_the_missing_target_and_claims_nothing() throws Exception {
		refused(UNKNOWN_USER, ONE_HUNDRED_PAMP, A_KEY).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value(containsString("does not exist")));

		assertThat(claimedKeys()).isZero();
	}

	@Test
	void a_submission_with_no_key_is_processed_normally_every_time() throws Exception {
		long before = ordersInTheLedger();

		submit(SEEDED_USER, ONE_HUNDRED_PAMP).andExpect(status().isCreated());
		submit(SEEDED_USER, ONE_HUNDRED_PAMP).andExpect(status().isCreated());

		assertThat(ordersInTheLedger()).isEqualTo(before + 2);
	}

	@Test
	void a_blank_key_is_refused_and_writes_nothing() throws Exception {
		refused(SEEDED_USER, ONE_HUNDRED_PAMP, "   ").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("Idempotency-Key")));
	}

	@Test
	void a_key_longer_than_the_column_is_refused_and_writes_nothing() throws Exception {
		refused(SEEDED_USER, ONE_HUNDRED_PAMP, "k".repeat(256)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("255")));
	}

	@Test
	void a_key_whose_request_was_refused_can_be_sent_again() throws Exception {
		refused(SEEDED_USER, marketOrderOf(ARS, "BUY", sizeOf(1)), A_KEY).andExpect(status().isBadRequest());

		submit(SEEDED_USER, ONE_HUNDRED_PAMP, A_KEY).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("FILLED"));
	}

}
