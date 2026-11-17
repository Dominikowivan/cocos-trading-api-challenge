package com.cocos.trading.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.cocos.trading.model.order.cancellation.CancellationDecision;
import com.cocos.trading.model.order.cancellation.CancellationReason;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.persistence.entity.OrderStatusHistoryEntity;
import com.cocos.trading.persistence.mapper.OrderMapper;

class OrderCancellationPersistenceTest extends PersistenceTestBase {

	private static final int SEEDED_USER = 1;

	private static final int NEVER_TRADED_USER = 2;

	private static final int PENDING_BUY = 5;

	private static final int FILLED_ORDER = 2;

	private static final int UNKNOWN_ORDER = 999;

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 15, 4, 5, 123456000);

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OrderStatusHistoryRepository orderStatusHistoryRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@AfterEach
	void restoreTheSeed() {
		jdbcTemplate.update("UPDATE orders SET status = 'NEW' WHERE id = ?", PENDING_BUY);
		jdbcTemplate.update("DELETE FROM order_status_history WHERE order_id = ?", PENDING_BUY);
	}

	@Test
	void the_users_own_order_is_read_with_its_instrument() {
		NewOrder order = inATransaction(
				() -> orderRepository.findPlacedOrderOf(SEEDED_USER, PENDING_BUY).orElseThrow());

		assertThat(order.instrument().instrumentId()).isEqualTo(45);
		assertThat(order.instrument().ticker()).isEqualTo("LOMA");
		assertThat(order.status()).isEqualTo(OrderStatus.NEW);
	}

	@Test
	void another_users_order_and_a_missing_one_are_both_absent() {
		assertThat(inATransaction(() -> orderRepository.findPlacedOrderOf(NEVER_TRADED_USER, PENDING_BUY))).isEmpty();
		assertThat(inATransaction(() -> orderRepository.findPlacedOrderOf(SEEDED_USER, UNKNOWN_ORDER))).isEmpty();
	}

	@Test
	void an_order_id_beyond_the_column_has_no_column_value() {
		assertThat(OrderMapper.toIdColumn(Integer.MAX_VALUE + 1L)).isEmpty();
		assertThat(OrderMapper.toIdColumn(PENDING_BUY)).contains(PENDING_BUY);
	}

	@Test
	void the_guarded_transition_moves_a_new_order_and_nothing_else() {
		Map<String, Object> before = rowOf(PENDING_BUY);

		boolean moved = inATransaction(
				() -> orderRepository.moveStatus(PENDING_BUY, OrderStatus.NEW, OrderStatus.CANCELLED));

		Map<String, Object> after = rowOf(PENDING_BUY);
		assertThat(moved).isTrue();
		assertThat(after).containsEntry("status", "CANCELLED");
		after.remove("status");
		before.remove("status");
		assertThat(after).isEqualTo(before);
	}

	@Test
	void the_guarded_transition_changes_nothing_once_the_order_left_new() {
		assertThat(
				inATransaction(() -> orderRepository.moveStatus(FILLED_ORDER, OrderStatus.NEW, OrderStatus.CANCELLED)))
			.isFalse();
		assertThat(statusOf(FILLED_ORDER)).isEqualTo("FILLED");

		inATransaction(() -> orderRepository.moveStatus(PENDING_BUY, OrderStatus.NEW, OrderStatus.CANCELLED));
		assertThat(
				inATransaction(() -> orderRepository.moveStatus(PENDING_BUY, OrderStatus.NEW, OrderStatus.CANCELLED)))
			.isFalse();
	}

	@Test
	void the_cancellation_is_recorded_with_the_users_reason_at_the_instant_passed_in() {
		inATransaction(() -> {
			orderStatusHistoryRepository.appendCancellationOf(PENDING_BUY, new CancellationDecision.Cancellation(
					OrderStatus.CANCELLED, CancellationReason.USER_REQUESTED, NOW));
			return null;
		});

		List<OrderStatusHistoryEntity> history = orderStatusHistoryRepository.findByOrderId(PENDING_BUY);
		assertThat(history).singleElement().satisfies(transition -> {
			assertThat(transition.getStatus()).isEqualTo("CANCELLED");
			assertThat(transition.getReason()).isEqualTo("USER_REQUESTED");
			assertThat(transition.getChangedAt()).isEqualTo(NOW);
		});
	}

	private <T> T inATransaction(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}

	private Map<String, Object> rowOf(int orderId) {
		return jdbcTemplate.queryForMap("SELECT * FROM orders WHERE id = ?", orderId);
	}

	private String statusOf(int orderId) {
		return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
	}

}
