package com.cocos.trading.persistence.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.cocos.trading.persistence.PersistenceTestBase;
import com.cocos.trading.service.submission.OrderSubmissionService;
import com.cocos.trading.service.submission.SubmissionRequest;

class LedgerAuditAgreementTest extends PersistenceTestBase {

	private static final int SEEDED_ORDERS = 11;

	private static final long SEEDED_USER = 1;

	private static final long PAMP = 47;

	private static final int NO_SIZE_DERIVED = 0;

	@Autowired
	private OrderSubmissionService orderSubmissionService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void submitOneOfEveryOutcome() {
		orderSubmissionService.submit(marketOrder("BUY", size(10), null));
		orderSubmissionService.submit(marketOrder("BUY", null, amount("100000.00")));
		orderSubmissionService.submit(marketOrder("SELL", size(10), null));
		orderSubmissionService.submit(limitOrder("BUY", size(100), null, "900.00"));
		orderSubmissionService.submit(limitOrder("SELL", null, amount("30000.00"), "1000.00"));
		orderSubmissionService.submit(marketOrder("BUY", size(1000000), null));
		orderSubmissionService.submit(marketOrder("SELL", size(1000000), null));
		orderSubmissionService.submit(marketOrder("BUY", null, amount("500.00")));
	}

	@AfterEach
	void restoreTheSeed() {
		jdbcTemplate.update("DELETE FROM order_status_history WHERE order_id > ?", SEEDED_ORDERS);
		jdbcTemplate.update("DELETE FROM orders WHERE id > ?", SEEDED_ORDERS);
	}

	@Test
	void every_order_this_feature_wrote_has_exactly_one_history_row() {
		assertThat(writtenOrders()).isNotEmpty()
			.allSatisfy(order -> assertThat(order).containsEntry("transitions", 1L));
	}

	@Test
	void the_history_row_states_the_status_the_order_is_in() {
		assertThat(writtenOrders())
			.allSatisfy(order -> assertThat(order).containsEntry("history_status", order.get("status")));
	}

	@Test
	void only_a_rejection_carries_a_reason_and_every_rejection_carries_one() {
		assertThat(writtenOrders()).allSatisfy(order -> {
			if ("REJECTED".equals(order.get("status"))) {
				assertThat(order.get("reason")).isNotNull();
			}
			else {
				assertThat(order.get("reason")).isNull();
			}
		});
		assertThat(writtenOrders()).anySatisfy(order -> assertThat(order).containsEntry("status", "REJECTED"));
	}

	@Test
	void no_order_was_written_without_a_price() {
		assertThat(writtenOrders()).allSatisfy(order -> assertThat(order.get("price")).isNotNull());
	}

	@Test
	void a_size_of_zero_only_ever_belongs_to_a_rejection_that_derived_none() {
		assertThat(writtenOrders()).allSatisfy(order -> {
			if (NO_SIZE_DERIVED == ((Number) order.get("size")).intValue()) {
				assertThat(order).containsEntry("status", "REJECTED").containsEntry("reason", "SIZE_BELOW_ONE_SHARE");
			}
		});
		assertThat(writtenOrders())
			.anySatisfy(order -> assertThat(order).containsEntry("reason", "SIZE_BELOW_ONE_SHARE"));
	}

	@Test
	void the_ledger_and_the_audit_trail_agree_on_when_it_happened() {
		assertThat(writtenOrders())
			.allSatisfy(order -> assertThat(order).containsEntry("changed_at", order.get("datetime")));
	}

	private List<Map<String, Object>> writtenOrders() {
		return jdbcTemplate.queryForList("""
				SELECT o.id, o.status, o.size, o.price, o.datetime,
				       h.status AS history_status, h.reason, h.changed_at,
				       count(h.id) OVER (PARTITION BY o.id) AS transitions
				  FROM orders o JOIN order_status_history h ON h.order_id = o.id
				 WHERE o.id > ?
				 ORDER BY o.id
				""", SEEDED_ORDERS);
	}

	private static SubmissionRequest marketOrder(String side, BigDecimal size, BigDecimal amount) {
		return new SubmissionRequest(SEEDED_USER, PAMP, "MARKET", side, size, amount, null);
	}

	private static SubmissionRequest limitOrder(String side, BigDecimal size, BigDecimal amount, String price) {
		return new SubmissionRequest(SEEDED_USER, PAMP, "LIMIT", side, size, amount, new BigDecimal(price));
	}

	private static BigDecimal size(long size) {
		return BigDecimal.valueOf(size);
	}

	private static BigDecimal amount(String amount) {
		return new BigDecimal(amount);
	}

}
