package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

class CancellationRefusalTest extends CancellationWebTestBase {

	@ParameterizedTest
	@CsvSource({ "3, CANCELLED", "6, REJECTED", "2, FILLED", "1, FILLED" })
	void an_order_that_is_not_new_is_a_conflict_and_stays_as_it_was(long orderId, String status) throws Exception {
		unchangedBy(() -> cancel(SEEDED_USER, orderId), status().isConflict()).andExpect(jsonPath("$.detail")
			.value("Order " + orderId + " is " + status + "; only a NEW order can be cancelled"));

		assertThat(statusOf(orderId)).isEqualTo(status);
	}

	@Test
	void a_second_cancellation_of_the_same_order_is_a_conflict_and_releases_nothing_more() throws Exception {
		cancelled(SEEDED_USER, PENDING_BUY);

		unchangedBy(() -> cancel(SEEDED_USER, PENDING_BUY), status().isConflict())
			.andExpect(jsonPath("$.detail").value("Order 5 is CANCELLED; only a NEW order can be cancelled"));

		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo("663000.00");
		assertThat(historyOf(PENDING_BUY)).hasSize(1);
	}

	@Test
	void another_users_order_is_not_found_and_stays_new() throws Exception {
		unchangedBy(() -> cancel(NEVER_TRADED_USER, PENDING_BUY), status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Order 5 does not exist for user 2"));

		assertThat(statusOf(PENDING_BUY)).isEqualTo("NEW");
	}

	@Test
	void a_missing_order_is_answered_exactly_as_a_foreign_one() throws Exception {
		unchangedBy(() -> cancel(NEVER_TRADED_USER, UNKNOWN_ORDER), status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Order 999 does not exist for user 2"));
	}

	@Test
	void an_unknown_user_is_not_found() throws Exception {
		unchangedBy(() -> cancel(UNKNOWN_USER, PENDING_BUY), status().isNotFound())
			.andExpect(jsonPath("$.detail").value("User 999 does not exist"));
	}

	@ParameterizedTest
	@CsvSource({ "1, 0, 'An order id is a positive whole number, and 0 is not'",
			"1, -5, 'An order id is a positive whole number, and -5 is not'",
			"0, 5, 'A user id is a positive whole number, and 0 is not'" })
	void an_id_below_one_is_a_bad_request(long userId, long orderId, String detail) throws Exception {
		unchangedBy(() -> cancel(userId, orderId), status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(detail));
	}

	@Test
	void an_order_id_that_is_not_a_number_is_a_bad_request() throws Exception {
		unchangedBy(() -> mvc.perform(post("/api/v1/users/1/orders/abc/cancel")), status().isBadRequest());
	}

	@Test
	void an_order_id_beyond_any_stored_order_is_not_found() throws Exception {
		unchangedBy(() -> mvc.perform(post("/api/v1/users/1/orders/2147483648/cancel")), status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Order 2147483648 does not exist for user 1"));
	}

	@Test
	void the_path_has_no_trailing_slash_spelling() throws Exception {
		unchangedBy(() -> mvc.perform(post("/api/v1/users/1/orders/5/cancel/")), status().isNotFound());

		assertThat(statusOf(PENDING_BUY)).isEqualTo("NEW");
	}

	private ResultActions unchangedBy(Request request, ResultMatcher expected) throws Exception {
		long orders = ordersOf(SEEDED_USER);
		long transitions = historyRows();
		List<String> statuses = seededStatuses();

		ResultActions response = request.perform().andExpect(expected);

		assertThat(ordersOf(SEEDED_USER)).isEqualTo(orders);
		assertThat(historyRows()).isEqualTo(transitions);
		assertThat(seededStatuses()).isEqualTo(statuses);
		return response;
	}

	private List<String> seededStatuses() {
		return LongStream.rangeClosed(1, 11).mapToObj(this::statusOf).toList();
	}

	@FunctionalInterface
	private interface Request {

		ResultActions perform() throws Exception;

	}

}
