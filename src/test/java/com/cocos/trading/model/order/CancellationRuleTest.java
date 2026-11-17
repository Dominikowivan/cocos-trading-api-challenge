package com.cocos.trading.model.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CancellationRuleTest {

	private static final long ORDER = 5;

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 15, 4, 5, 123456000);

	@Test
	void a_new_order_is_cancelled_by_the_user_at_the_instant_passed_in() {
		assertThat(CancellationRule.decide(ORDER, OrderStatus.NEW, NOW)).isEqualTo(
				new CancellationDecision.Cancellation(OrderStatus.CANCELLED, CancellationReason.USER_REQUESTED, NOW));
	}

	@ParameterizedTest
	@EnumSource(value = OrderStatus.class, names = { "FILLED", "REJECTED", "CANCELLED" })
	void an_order_that_is_not_new_is_not_cancellable_and_the_answer_names_it(OrderStatus status) {
		assertThat(CancellationRule.decide(ORDER, status, NOW))
			.isEqualTo(new CancellationDecision.NotCancellable(ORDER, status));
	}

}
