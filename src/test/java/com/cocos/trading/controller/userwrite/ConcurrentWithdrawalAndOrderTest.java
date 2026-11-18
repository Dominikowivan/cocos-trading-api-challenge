package com.cocos.trading.controller.userwrite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.RepeatedTest;

import com.cocos.trading.controller.CashTransferWebTestBase;
import com.cocos.trading.controller.Simultaneously;

class ConcurrentWithdrawalAndOrderTest extends CashTransferWebTestBase {

	private static final String FOUR_HUNDRED_PAMP = marketOrderOf(PAMP, "BUY", sizeOf(400));

	@RepeatedTest(5)
	void a_withdrawal_and_a_purchase_cannot_spend_the_same_pesos() throws Exception {
		long before = ordersInTheLedger();

		List<String> outcomes = Simultaneously.theseTwo(() -> withdrawn(SEEDED_USER, 400000),
				() -> createdBodyOf(submit(SEEDED_USER, FOUR_HUNDRED_PAMP)));

		assertThat(statusesIn(outcomes)).containsExactlyInAnyOrder("FILLED", "REJECTED");
		assertThat(rejectionIn(outcomes)).isEqualTo("INSUFFICIENT_FUNDS");
		assertThat(ordersInTheLedger()).isEqualTo(before + 2);
		assertThat(availableCashOf(SEEDED_USER)).isNotNegative();
	}

}
