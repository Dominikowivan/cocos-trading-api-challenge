package com.cocos.trading.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.cocos.trading.model.Price;
import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.order.CancellationReason;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.OrderKind;
import com.cocos.trading.model.order.OrderSide;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.order.RecordedTransition;
import com.cocos.trading.model.order.RejectionReason;
import com.cocos.trading.model.user.UserId;
import com.cocos.trading.persistence.entity.OrderStatusHistoryEntity;
import com.cocos.trading.persistence.mapper.OrderMapper;

class OrderStatusHistoryReasonTest extends PersistenceTestBase {

	private static final int SEEDED_ORDERS = 11;

	private static final InstrumentRef PAMP = new InstrumentRef(47, "PAMP", "Pampa Holding S.A.", "ACCIONES");

	private static final UserId NEVER_TRADED_USER = new UserId(2);

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
		jdbcTemplate.update("DELETE FROM order_status_history WHERE order_id > ?", SEEDED_ORDERS);
		jdbcTemplate.update("DELETE FROM orders WHERE id > ?", SEEDED_ORDERS);
	}

	@Test
	void a_cancelled_row_is_read_as_a_cancellation_and_never_as_a_rejection() {
		Integer orderId = appended(limitBuy(OrderStatus.NEW, Optional.empty()));
		cancelledWithJdbc(orderId);

		assertThat(transitionsOf(orderId)).containsExactly(
				new RecordedTransition(OrderStatus.NEW, Optional.empty(), Optional.empty()), new RecordedTransition(
						OrderStatus.CANCELLED, Optional.empty(), Optional.of(CancellationReason.USER_REQUESTED)));
	}

	@Test
	void a_rejection_is_still_read_with_its_reason() {
		Integer orderId = appended(limitBuy(OrderStatus.REJECTED, Optional.of(RejectionReason.INSUFFICIENT_FUNDS)));

		assertThat(transitionsOf(orderId)).containsExactly(new RecordedTransition(OrderStatus.REJECTED,
				Optional.of(RejectionReason.INSUFFICIENT_FUNDS), Optional.empty()));
	}

	private List<RecordedTransition> transitionsOf(Integer orderId) {
		return orderStatusHistoryRepository.findByOrderId(orderId)
			.stream()
			.sorted(Comparator.comparing(OrderStatusHistoryEntity::getId))
			.map(OrderMapper::toRecordedTransition)
			.toList();
	}

	private Integer appended(NewOrder order) {
		return new TransactionTemplate(transactionManager).execute(status -> {
			Integer orderId = orderRepository.append(order);
			orderStatusHistoryRepository.appendTransitionOf(orderId, order);
			return orderId;
		});
	}

	private void cancelledWithJdbc(Integer orderId) {
		jdbcTemplate.update("UPDATE orders SET status = 'CANCELLED' WHERE id = ?", orderId);
		jdbcTemplate.update("""
				INSERT INTO order_status_history (order_id, status, reason, changed_at)
				VALUES (?, 'CANCELLED', 'USER_REQUESTED', now())
				""", orderId);
	}

	private static NewOrder limitBuy(OrderStatus status, Optional<RejectionReason> reason) {
		return new NewOrder(NEVER_TRADED_USER, PAMP, OrderSide.BUY, OrderKind.LIMIT, status, BigDecimal.TEN,
				Price.of(new BigDecimal("900.00")), reason, LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
	}

}
