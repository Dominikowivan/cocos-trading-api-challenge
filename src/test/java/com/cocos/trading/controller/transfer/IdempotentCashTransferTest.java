package com.cocos.trading.controller.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.cocos.trading.controller.CashTransferWebTestBase;

class IdempotentCashTransferTest extends CashTransferWebTestBase {

	private static final String DEP_1 = "dep-1";

	private static final String W_1 = "w-1";

	@Test
	void the_identical_retry_returns_the_first_transfer_and_moves_the_money_once() throws Exception {
		long before = ordersInTheLedger();

		String first = createdBodyOf(transfer(NEVER_TRADED_USER, depositOf(100000), DEP_1));
		String retry = createdBodyOf(transfer(NEVER_TRADED_USER, depositOf(100000), DEP_1));

		assertThat(reportedAs(retry, "orderId")).isEqualTo(reportedAs(first, "orderId"));
		assertThat(reportedAs(retry, "status")).isEqualTo("FILLED");
		assertThat(reportedAs(retry, "transferredAt")).isEqualTo(reportedAs(first, "transferredAt"));
		assertThat(ordersInTheLedger()).isEqualTo(before + 1);
		assertThat(availableCashOf(NEVER_TRADED_USER)).isEqualByComparingTo("100000.00");
	}

	@Test
	void the_same_key_with_a_different_transfer_is_a_conflict_that_writes_nothing() throws Exception {
		transfer(NEVER_TRADED_USER, depositOf(100000), DEP_1).andExpect(status().isCreated());

		refusedTransfer(NEVER_TRADED_USER, withdrawalOf(100000), DEP_1).andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(containsString(DEP_1)));

		assertThat(availableCashOf(NEVER_TRADED_USER)).isEqualByComparingTo("100000.00");
	}

	@Test
	void the_same_amount_written_with_decimals_is_the_same_request() throws Exception {
		String first = createdBodyOf(transfer(NEVER_TRADED_USER, depositOf(100000), DEP_1));
		String retry = createdBodyOf(transfer(NEVER_TRADED_USER, transferOf("CASH_IN", "100000.00"), DEP_1));

		assertThat(reportedAs(retry, "orderId")).isEqualTo(reportedAs(first, "orderId"));
	}

	@Test
	void the_same_key_sent_by_another_user_is_a_new_request() throws Exception {
		String first = createdBodyOf(transfer(NEVER_TRADED_USER, depositOf(100000), DEP_1));
		String other = createdBodyOf(transfer(USER_THREE, depositOf(100000), DEP_1));

		assertThat(reportedAs(other, "orderId")).isNotEqualTo(reportedAs(first, "orderId"));
		assertThat(reportedAs(other, "userId")).isEqualTo(Long.toString(USER_THREE));
		assertThat(availableCashOf(USER_THREE)).isEqualByComparingTo("100000.00");
	}

	@Test
	void a_rejected_withdrawal_is_replayed_and_not_decided_again() throws Exception {
		String first = createdBodyOf(transfer(NEVER_TRADED_USER, withdrawalOf(200000), W_1));
		deposited(NEVER_TRADED_USER, 500000);

		String retry = createdBodyOf(transfer(NEVER_TRADED_USER, withdrawalOf(200000), W_1));

		assertThat(reportedAs(first, "status")).isEqualTo("REJECTED");
		assertThat(reportedAs(retry, "status")).isEqualTo("REJECTED");
		assertThat(reportedAs(retry, "rejectionReason")).isEqualTo("INSUFFICIENT_FUNDS");
		assertThat(reportedAs(retry, "orderId")).isEqualTo(reportedAs(first, "orderId"));
		assertThat(availableCashOf(NEVER_TRADED_USER)).isEqualByComparingTo("500000.00");
	}

	@Test
	void a_transfer_with_no_key_is_processed_normally_every_time() throws Exception {
		long before = ordersInTheLedger();

		deposited(NEVER_TRADED_USER, 100000);
		deposited(NEVER_TRADED_USER, 100000);

		assertThat(ordersInTheLedger()).isEqualTo(before + 2);
		assertThat(availableCashOf(NEVER_TRADED_USER)).isEqualByComparingTo("200000.00");
	}

	@Test
	void a_blank_key_is_refused_and_writes_nothing() throws Exception {
		refusedTransfer(NEVER_TRADED_USER, depositOf(100000), "   ").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("Idempotency-Key")));

		assertThat(claimedKeys()).isZero();
	}

	@Test
	void a_key_longer_than_the_column_is_refused_and_writes_nothing() throws Exception {
		refusedTransfer(NEVER_TRADED_USER, depositOf(100000), "k".repeat(256)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("255")));

		assertThat(claimedKeys()).isZero();
	}

	@Test
	void an_unknown_user_sending_a_key_is_the_missing_target_and_claims_nothing() throws Exception {
		refusedTransfer(UNKNOWN_USER, depositOf(100000), DEP_1).andExpect(status().isNotFound());

		assertThat(claimedKeys()).isZero();
	}

	@Test
	void a_replay_answers_without_ever_taking_the_users_lock() throws Exception {
		String first = createdBodyOf(transfer(NEVER_TRADED_USER, depositOf(100000), DEP_1));

		try (Connection heldByAnotherTransaction = lockedUser(NEVER_TRADED_USER)) {
			String retry = createdBodyOf(transfer(NEVER_TRADED_USER, depositOf(100000), DEP_1));

			assertThat(reportedAs(retry, "orderId")).isEqualTo(reportedAs(first, "orderId"));
			heldByAnotherTransaction.rollback();
		}
	}

}
