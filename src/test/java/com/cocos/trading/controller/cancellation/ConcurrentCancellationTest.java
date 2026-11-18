package com.cocos.trading.controller.cancellation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.RepeatedTest;

import com.cocos.trading.controller.CancellationWebTestBase;
import com.cocos.trading.controller.Simultaneously;

class ConcurrentCancellationTest extends CancellationWebTestBase {

	@RepeatedTest(5)
	void two_cancellations_of_one_order_succeed_exactly_once() throws Exception {
		List<Integer> answers = Simultaneously.theseTwo(() -> answerTo(SEEDED_USER, PENDING_BUY),
				() -> answerTo(SEEDED_USER, PENDING_BUY));

		assertThat(answers).containsExactlyInAnyOrder(200, 409);
		assertThat(historyOf(PENDING_BUY)).containsExactly(new Transition("CANCELLED", "USER_REQUESTED"));
		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo("663000.00");
	}

	private int answerTo(long userId, long orderId) throws Exception {
		return cancel(userId, orderId).andReturn().getResponse().getStatus();
	}

}
