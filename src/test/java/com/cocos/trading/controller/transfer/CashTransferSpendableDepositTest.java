package com.cocos.trading.controller.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.cocos.trading.controller.CashTransferWebTestBase;

class CashTransferSpendableDepositTest extends CashTransferWebTestBase {

	@Test
	void a_deposit_can_be_spent_by_the_next_order() throws Exception {
		deposited(USER_FOUR, 500000);

		String order = createdBodyOf(submit(USER_FOUR, marketOrderOf(PAMP, "BUY", sizeOf(100))));

		assertThat(reportedAs(order, "status")).isEqualTo("FILLED");
		assertThat(amountIn(order, "price")).isEqualByComparingTo("925.85");
		assertThat(availableCashOf(USER_FOUR)).isEqualByComparingTo("407415.00");
	}

}
