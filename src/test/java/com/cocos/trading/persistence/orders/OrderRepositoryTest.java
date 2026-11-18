package com.cocos.trading.persistence.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.cocos.trading.model.order.Order;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.persistence.OrderRepository;
import com.cocos.trading.persistence.PersistenceTestBase;
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
		List<Order> orders = ordersOf(SEEDED);

		assertThat(orders).hasSize(9)
			.allSatisfy(order -> assertThat(order.status()).isIn(OrderStatus.FILLED, OrderStatus.NEW));
	}

	@Test
	void rows_come_back_in_datetime_then_id_order() {
		List<Order> orders = ordersOf(SEEDED);

		assertThat(orders).isSortedAccordingTo(Comparator.comparing(Order::occurredAt).thenComparing(Order::orderId));
	}

	@Test
	void the_instrument_is_fetched_with_the_order() {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		List<Order> orders = ordersOf(SEEDED);

		assertThat(orders).isNotEmpty().allSatisfy(order -> assertThat(order.instrument().ticker()).isNotBlank());
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
	}

	@Test
	void another_users_rows_never_appear() {
		assertThat(ordersOf(NEVER_TRADED)).isEmpty();
	}

	@Test
	void the_cash_instrument_is_mapped_as_cash() {
		List<Order> orders = ordersOf(SEEDED);

		assertThat(orders).anySatisfy(order -> assertThat(order.instrument().isCash()).isTrue());
	}

	private List<Order> ordersOf(Integer userId) {
		return OrderMapper
			.toOrders(orderRepository.findOrdersOf(userId, OrderMapper.toStatusColumns(OrderStatus.contributing())));
	}

}
