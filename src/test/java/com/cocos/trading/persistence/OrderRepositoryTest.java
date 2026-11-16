package com.cocos.trading.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.cocos.trading.model.order.Order;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.persistence.mapper.OrderMapper;

import jakarta.persistence.EntityManagerFactory;

class OrderRepositoryTest extends PersistenceTestBase {

	private static final Integer SEEDED = 1;

	private static final Integer NEVER_TRADED = 2;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Test
	void cancelled_and_rejected_rows_are_never_returned() {
		List<Order> ledger = ledgerOf(SEEDED);

		assertThat(ledger).isNotEmpty();
		assertThat(ledger).allSatisfy(order -> assertThat(order.status()).isIn(OrderStatus.FILLED, OrderStatus.NEW));
		assertThat(ledger).hasSize(9);
	}

	@Test
	void rows_come_back_in_datetime_then_id_order() {
		List<Order> ledger = ledgerOf(SEEDED);

		assertThat(ledger).isSortedAccordingTo(
				Comparator.comparing(Order::occurredAt).thenComparing(Order::orderId));
	}

	@Test
	void the_instrument_is_fetched_with_the_order() {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		List<Order> ledger = ledgerOf(SEEDED);

		assertThat(ledger).allSatisfy(order -> assertThat(order.instrument().ticker()).isNotBlank());
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	void another_users_rows_never_appear() {
		assertThat(ledgerOf(NEVER_TRADED)).isEmpty();
	}

	@Test
	void the_cash_instrument_is_mapped_as_cash() {
		List<Order> ledger = ledgerOf(SEEDED);

		assertThat(ledger).anySatisfy(order -> assertThat(order.instrument().isCash()).isTrue());
	}

	private List<Order> ledgerOf(Integer userId) {
		return OrderMapper.toOrders(
				orderRepository.findLedgerOf(userId, OrderMapper.toStatusColumns(OrderStatus.contributing())));
	}

}
