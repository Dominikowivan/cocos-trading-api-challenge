package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

class CashTransferKeySharedWithOrdersTest extends CashTransferWebTestBase {

	private static final String ONE_HUNDRED_PAMP = marketOrderOf(PAMP, "BUY", sizeOf(100));

	@Test
	void a_key_already_used_for_an_order_is_a_conflict_on_a_transfer() throws Exception {
		submit(SEEDED_USER, ONE_HUNDRED_PAMP, "k-1").andExpect(status().isCreated());

		refusedTransfer(SEEDED_USER, depositOf(100000), "k-1").andExpect(status().isConflict());

		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo("534915.00");
	}

	@Test
	void a_key_already_used_for_a_transfer_is_a_conflict_on_an_order() throws Exception {
		transfer(SEEDED_USER, depositOf(100000), "t-1").andExpect(status().isCreated());

		refused(SEEDED_USER, ONE_HUNDRED_PAMP, "t-1").andExpect(status().isConflict());

		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo("727500.00");
	}

}
