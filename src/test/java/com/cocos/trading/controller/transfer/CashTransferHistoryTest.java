package com.cocos.trading.controller.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cocos.trading.controller.CashTransferWebTestBase;

class CashTransferHistoryTest extends CashTransferWebTestBase {

	@Test
	void a_filled_deposit_leaves_one_order_and_one_transition_with_no_reason() throws Exception {
		long orderId = Long.parseLong(reportedAs(deposited(NEVER_TRADED_USER, 250000), "orderId"));

		assertThat(rowOf(orderId)).containsEntry("side", "CASH_IN").containsEntry("status", "FILLED");
		assertTheLedgerShapeOf(orderId, 250000);
		assertThat(historyOf(orderId)).singleElement().satisfies(transition -> {
			assertThat(transition).containsEntry("status", "FILLED");
			assertThat(transition.get("reason")).isNull();
		});
	}

	@Test
	void a_rejected_withdrawal_leaves_one_order_and_one_transition_with_its_reason() throws Exception {
		long orderId = Long.parseLong(reportedAs(withdrawn(USER_THREE, 40), "orderId"));

		assertThat(rowOf(orderId)).containsEntry("side", "CASH_OUT").containsEntry("status", "REJECTED");
		assertTheLedgerShapeOf(orderId, 40);
		assertThat(historyOf(orderId)).singleElement().satisfies(transition -> {
			assertThat(transition).containsEntry("status", "REJECTED");
			assertThat(transition).containsEntry("reason", "INSUFFICIENT_FUNDS");
		});
	}

	private void assertTheLedgerShapeOf(long orderId, int pesos) {
		Map<String, Object> row = rowOf(orderId);

		assertThat(row).containsEntry("type", "MARKET").containsEntry("instrumentid", (int) ARS);
		assertThat(row).containsEntry("size", pesos);
		assertThat((BigDecimal) row.get("price")).isEqualByComparingTo("1.00");
	}

	private Map<String, Object> rowOf(long orderId) {
		return jdbc().queryForMap("SELECT instrumentid, size, price, type, side, status FROM orders WHERE id = ?",
				orderId);
	}

	private List<Map<String, Object>> historyOf(long orderId) {
		return jdbc().queryForList("SELECT status, reason FROM order_status_history WHERE order_id = ?", orderId);
	}

}
