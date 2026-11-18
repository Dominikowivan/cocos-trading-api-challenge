package com.cocos.trading.persistence.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
import com.cocos.trading.model.order.CashMovement;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.order.OrderType;
import com.cocos.trading.model.order.RejectionReason;
import com.cocos.trading.model.user.UserId;
import com.cocos.trading.persistence.OrderRepository;
import com.cocos.trading.persistence.OrderStatusHistoryRepository;
import com.cocos.trading.persistence.PersistenceTestBase;
import com.cocos.trading.persistence.entity.OrderStatusHistoryEntity;

class CashOrderAppendTest extends PersistenceTestBase {

	private static final int SEEDED_ORDERS = 11;

	private static final InstrumentRef ARS = new InstrumentRef(66, "ARS", "PESOS", "MONEDA");

	private static final UserId NEVER_TRADED_USER = new UserId(2);

	private static final BigDecimal ONE_HUNDRED_THOUSAND = BigDecimal.valueOf(100000);

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
	void a_filled_deposit_is_read_back_with_its_cash_side() {
		Integer orderId = appended(cashOrder(CashMovement.CASH_IN, OrderStatus.FILLED, Optional.empty()));

		NewOrder read = readBack(orderId);

		assertThat(read.side()).isEqualTo(CashMovement.CASH_IN);
		assertThat(read.status()).isEqualTo(OrderStatus.FILLED);
		assertThat(read.type()).isEqualTo(OrderType.MARKET);
		assertThat(read.instrument().isCash()).isTrue();
		assertThat(read.size()).isEqualByComparingTo(ONE_HUNDRED_THOUSAND);
		assertThat(read.price().amount()).isEqualByComparingTo(BigDecimal.ONE);
	}

	@Test
	void a_rejected_withdrawal_is_read_back_with_its_cash_side() {
		Integer orderId = appended(cashOrder(CashMovement.CASH_OUT, OrderStatus.REJECTED,
				Optional.of(RejectionReason.INSUFFICIENT_FUNDS)));

		NewOrder read = readBack(orderId);

		assertThat(read.side()).isEqualTo(CashMovement.CASH_OUT);
		assertThat(read.status()).isEqualTo(OrderStatus.REJECTED);
	}

	@Test
	void the_history_row_carries_a_reason_only_for_the_rejected_one() {
		Integer filled = appended(cashOrder(CashMovement.CASH_IN, OrderStatus.FILLED, Optional.empty()));
		Integer rejected = appended(cashOrder(CashMovement.CASH_OUT, OrderStatus.REJECTED,
				Optional.of(RejectionReason.INSUFFICIENT_FUNDS)));

		assertThat(historyOf(filled)).singleElement().satisfies(transition -> {
			assertThat(transition.getStatus()).isEqualTo("FILLED");
			assertThat(transition.getReason()).isNull();
		});
		assertThat(historyOf(rejected)).singleElement().satisfies(transition -> {
			assertThat(transition.getStatus()).isEqualTo("REJECTED");
			assertThat(transition.getReason()).isEqualTo("INSUFFICIENT_FUNDS");
		});
	}

	private Integer appended(NewOrder order) {
		return new TransactionTemplate(transactionManager).execute(status -> {
			Integer orderId = orderRepository.append(order);
			orderStatusHistoryRepository.appendTransitionOf(orderId, order);
			return orderId;
		});
	}

	private NewOrder readBack(Integer orderId) {
		return new TransactionTemplate(transactionManager)
			.execute(status -> orderRepository.findPlacedOrder(orderId).orElseThrow());
	}

	private List<OrderStatusHistoryEntity> historyOf(Integer orderId) {
		return orderStatusHistoryRepository.findByOrderId(orderId);
	}

	private static NewOrder cashOrder(CashMovement side, OrderStatus status, Optional<RejectionReason> reason) {
		return new NewOrder(NEVER_TRADED_USER, ARS, side, OrderType.MARKET, status, ONE_HUNDRED_THOUSAND,
				Price.of(BigDecimal.ONE), reason, LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
	}

}
