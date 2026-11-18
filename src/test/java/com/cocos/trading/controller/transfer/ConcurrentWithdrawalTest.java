package com.cocos.trading.controller.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import com.cocos.trading.controller.CashTransferWebTestBase;
import com.cocos.trading.controller.Simultaneously;

class ConcurrentWithdrawalTest extends CashTransferWebTestBase {

	@RepeatedTest(5)
	void two_withdrawals_that_cannot_both_be_paid_fill_exactly_one() throws Exception {
		long before = ordersInTheLedger();

		List<String> outcomes = Simultaneously.theseTwo(() -> withdrawn(SEEDED_USER, 400000),
				() -> withdrawn(SEEDED_USER, 400000));

		assertThat(statusesIn(outcomes)).containsExactlyInAnyOrder("FILLED", "REJECTED");
		assertThat(rejectionIn(outcomes)).isEqualTo("INSUFFICIENT_FUNDS");
		assertThat(ordersInTheLedger()).isEqualTo(before + 2);
		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo("227500.00");
	}

	@Test
	void two_withdrawals_that_both_fit_are_both_filled() throws Exception {
		List<String> outcomes = Simultaneously.theseTwo(() -> withdrawn(SEEDED_USER, 300000),
				() -> withdrawn(SEEDED_USER, 300000));

		assertThat(statusesIn(outcomes)).containsExactly("FILLED", "FILLED");
		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo("27500.00");
	}

}
