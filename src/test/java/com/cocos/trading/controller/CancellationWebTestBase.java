package com.cocos.trading.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.ResultActions;

abstract class CancellationWebTestBase extends CashTransferWebTestBase {

	protected static final String CANCEL = "/api/v1/users/{userId}/orders/{orderId}/cancel";

	protected static final long PENDING_BUY = 5;

	protected static final long OTHER_PENDING_BUY = 7;

	protected static final long ALREADY_CANCELLED = 3;

	protected static final long REJECTED_ORDER = 6;

	protected static final long FILLED_ORDER = 2;

	protected static final long CASH_IN_ORDER = 1;

	protected static final long UNKNOWN_ORDER = 999;

	private static final int SEEDED_ORDERS = 11;

	@AfterEach
	void restoreTheSeededStatuses() {
		jdbc().update("UPDATE orders SET status = 'NEW' WHERE id IN (?, ?)", PENDING_BUY, OTHER_PENDING_BUY);
		jdbc().update("DELETE FROM order_status_history WHERE order_id <= ?", SEEDED_ORDERS);
	}

	protected ResultActions cancel(long userId, long orderId) throws Exception {
		return mvc.perform(post(CANCEL, userId, orderId));
	}

	protected ResultActions cancel(long userId, long orderId, String idempotencyKey) throws Exception {
		return mvc.perform(post(CANCEL, userId, orderId).header(IDEMPOTENCY_KEY, idempotencyKey));
	}

	protected String cancelled(long userId, long orderId) throws Exception {
		return bodyOf(cancel(userId, orderId).andExpect(status().isOk()));
	}

	protected String statusOf(long orderId) {
		return jdbc().queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
	}

	protected List<Transition> historyOf(long orderId) {
		return jdbc().query("SELECT status, reason FROM order_status_history WHERE order_id = ? ORDER BY id",
				(row, number) -> new Transition(row.getString("status"), row.getString("reason")), orderId);
	}

	protected long historyRows() {
		return jdbc().queryForObject("SELECT count(*) FROM order_status_history", Long.class);
	}

	protected record Transition(String status, String reason) {

	}

}
