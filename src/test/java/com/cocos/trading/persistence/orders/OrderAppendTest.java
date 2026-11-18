package com.cocos.trading.persistence.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.cocos.trading.persistence.OrderStatusHistoryRepository;
import com.cocos.trading.persistence.PersistenceTestBase;
import com.cocos.trading.persistence.entity.OrderStatusHistoryEntity;
import com.cocos.trading.service.submission.OrderSubmissionService;
import com.cocos.trading.service.submission.SubmissionRequest;
import com.cocos.trading.service.submission.SubmittedOrder;

class OrderAppendTest extends PersistenceTestBase {

	private static final int SEEDED_ORDERS = 11;

	private static final long SEEDED_USER = 1;

	private static final long PAMP = 47;

	@Autowired
	private OrderSubmissionService orderSubmissionService;

	@Autowired
	private OrderStatusHistoryRepository orderStatusHistoryRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@AfterEach
	void restoreTheSeed() {
		jdbcTemplate.update("DELETE FROM order_status_history WHERE order_id > ?", SEEDED_ORDERS);
		jdbcTemplate.update("DELETE FROM orders WHERE id > ?", SEEDED_ORDERS);
	}

	@Test
	void one_submission_appends_exactly_one_order() {
		long before = orders();

		orderSubmissionService.submit(marketBuyOf(BigDecimal.TEN));

		assertThat(orders()).isEqualTo(before + 1);
	}

	@Test
	void the_order_carries_the_transition_it_was_born_into() {
		SubmittedOrder order = orderSubmissionService.submit(marketBuyOf(BigDecimal.TEN));

		List<OrderStatusHistoryEntity> history = historyOf(order);

		assertThat(history).singleElement().satisfies(transition -> {
			assertThat(transition.getStatus()).isEqualTo(order.status().name());
			assertThat(transition.getReason()).isNull();
			assertThat(transition.getChangedAt()).isEqualTo(order.submittedAt());
		});
	}

	@Test
	void a_rejection_is_appended_with_its_reason_like_any_other_outcome() {
		SubmittedOrder order = orderSubmissionService.submit(marketBuyOf(BigDecimal.valueOf(1000000)));

		assertThat(historyOf(order)).singleElement()
			.satisfies(transition -> assertThat(transition.getReason()).isEqualTo("INSUFFICIENT_FUNDS"));
	}

	@Test
	void a_rollback_leaves_neither_the_order_nor_its_transition() {
		long orders = orders();
		long transitions = transitions();

		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			orderSubmissionService.submit(marketBuyOf(BigDecimal.TEN));
			status.setRollbackOnly();
		});

		assertThat(orders()).isEqualTo(orders);
		assertThat(transitions()).isEqualTo(transitions);
	}

	private List<OrderStatusHistoryEntity> historyOf(SubmittedOrder order) {
		return orderStatusHistoryRepository.findByOrderId(Math.toIntExact(order.orderId()));
	}

	private long orders() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class);
	}

	private long transitions() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM order_status_history", Long.class);
	}

	private static SubmissionRequest marketBuyOf(BigDecimal size) {
		return new SubmissionRequest(SEEDED_USER, PAMP, "MARKET", "BUY", size, null, null);
	}

}
