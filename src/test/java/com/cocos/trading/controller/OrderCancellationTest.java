package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

class OrderCancellationTest extends CancellationWebTestBase {

	@Test
	void a_pending_buy_is_cancelled_and_answered_as_the_order() throws Exception {
		String body = cancelled(SEEDED_USER, PENDING_BUY);

		assertThat(reportedAs(body, "orderId")).isEqualTo("5");
		assertThat(reportedAs(body, "userId")).isEqualTo("1");
		assertThat(reportedAs(body, "instrumentId")).isEqualTo("45");
		assertThat(reportedAs(body, "ticker")).isEqualTo("LOMA");
		assertThat(reportedAs(body, "type")).isEqualTo("LIMIT");
		assertThat(reportedAs(body, "side")).isEqualTo("BUY");
		assertThat(reportedAs(body, "size")).isEqualTo("50");
		assertThat(amountIn(body, "price")).isEqualByComparingTo("710.00");
		assertThat(reportedAs(body, "status")).isEqualTo("CANCELLED");
		assertThat((Object) JsonPath.read(body, "$.rejectionReason")).isNull();
	}

	@Test
	void the_reservation_is_released_and_settled_cash_does_not_move() throws Exception {
		cancelled(SEEDED_USER, PENDING_BUY);

		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo("663000.00");
		assertThat(settledCashOf(SEEDED_USER)).isEqualByComparingTo("753000.00");
	}

	@Test
	void the_cancellation_is_one_history_row_and_only_the_status_changes() throws Exception {
		Map<String, Object> before = rowOf(PENDING_BUY);

		cancelled(SEEDED_USER, PENDING_BUY);

		Map<String, Object> after = rowOf(PENDING_BUY);
		assertThat(historyOf(PENDING_BUY)).containsExactly(new Transition("CANCELLED", "USER_REQUESTED"));
		assertThat(after.remove("status")).isEqualTo("CANCELLED");
		before.remove("status");
		assertThat(after).isEqualTo(before);
	}

	@Test
	void cancelling_both_pending_orders_leaves_nothing_reserved() throws Exception {
		cancelled(SEEDED_USER, PENDING_BUY);
		cancelled(SEEDED_USER, OTHER_PENDING_BUY);

		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo("753000.00");
		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo(settledCashOf(SEEDED_USER));
	}

	private Map<String, Object> rowOf(long orderId) {
		return jdbc().queryForMap("SELECT * FROM orders WHERE id = ?", orderId);
	}

}
