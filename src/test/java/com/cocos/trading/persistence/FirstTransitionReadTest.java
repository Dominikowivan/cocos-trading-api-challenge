package com.cocos.trading.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.cocos.trading.model.Price;
import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.order.CancellationDecision;
import com.cocos.trading.model.order.CancellationReason;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.OrderKind;
import com.cocos.trading.model.order.OrderSide;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.order.RecordedTransition;
import com.cocos.trading.model.order.RejectionReason;
import com.cocos.trading.model.user.UserId;

class FirstTransitionReadTest extends PersistenceTestBase {

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
	void a_cancelled_order_was_first_recorded_new_and_later_cancelled_by_the_user() {
		Integer orderId = appended(limitBuy(OrderStatus.NEW, Optional.empty()));
		orderStatusHistoryRepository.appendCancellationOf(orderId, new CancellationDecision.Cancellation(
				OrderStatus.CANCELLED, CancellationReason.USER_REQUESTED, LocalDateTime.now()));

		assertThat(orderStatusHistoryRepository.firstTransitionOf(orderId))
			.contains(new RecordedTransition(OrderStatus.NEW, Optional.empty(), Optional.empty()));
		assertThat(orderStatusHistoryRepository.cancellationOf(orderId)).contains(new RecordedTransition(
				OrderStatus.CANCELLED, Optional.empty(), Optional.of(CancellationReason.USER_REQUESTED)));
	}

	@Test
	void a_rejection_is_first_recorded_with_its_reason_and_was_never_cancelled() {
		Integer orderId = appended(limitBuy(OrderStatus.REJECTED, Optional.of(RejectionReason.INSUFFICIENT_FUNDS)));

		assertThat(orderStatusHistoryRepository.firstTransitionOf(orderId)).contains(new RecordedTransition(
				OrderStatus.REJECTED, Optional.of(RejectionReason.INSUFFICIENT_FUNDS), Optional.empty()));
		assertThat(orderStatusHistoryRepository.cancellationOf(orderId)).isEmpty();
	}

	private Integer appended(NewOrder order) {
		return new TransactionTemplate(transactionManager).execute(status -> {
			Integer orderId = orderRepository.append(order);
			orderStatusHistoryRepository.appendTransitionOf(orderId, order);
			return orderId;
		});
	}

	private static NewOrder limitBuy(OrderStatus status, Optional<RejectionReason> reason) {
		return new NewOrder(NEVER_TRADED_USER, PAMP, OrderSide.BUY, OrderKind.LIMIT, status, BigDecimal.TEN,
				Price.of(new BigDecimal("900.00")), reason, LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
	}

}
