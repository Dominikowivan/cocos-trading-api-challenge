package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CashTransferDepositWithdrawTest extends CashTransferWebTestBase {

	@Test
	void a_deposit_is_filled_and_reported_in_its_own_shape() throws Exception {
		transfer(NEVER_TRADED_USER, depositOf(100000)).andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.userId").value(NEVER_TRADED_USER))
			.andExpect(jsonPath("$.side").value("CASH_IN"))
			.andExpect(jsonPath("$.status").value("FILLED"))
			.andExpect(jsonPath("$.rejectionReason").value(nullValue()))
			.andExpect(content().string(containsString("\"amount\":100000.00")));
	}

	@Test
	void a_deposit_raises_settled_and_available_cash_by_its_amount() throws Exception {
		deposited(NEVER_TRADED_USER, 100000);

		assertThat(settledCashOf(NEVER_TRADED_USER)).isEqualByComparingTo("100000.00");
		assertThat(availableCashOf(NEVER_TRADED_USER)).isEqualByComparingTo("100000.00");
	}

	@Test
	void a_withdrawal_one_peso_above_what_is_available_is_recorded_rejected_and_moves_nothing() throws Exception {
		deposited(NEVER_TRADED_USER, 100000);

		String rejected = withdrawn(NEVER_TRADED_USER, 100001);

		assertThat(reportedAs(rejected, "side")).isEqualTo("CASH_OUT");
		assertThat(reportedAs(rejected, "status")).isEqualTo("REJECTED");
		assertThat(reportedAs(rejected, "rejectionReason")).isEqualTo("INSUFFICIENT_FUNDS");
		assertThat(availableCashOf(NEVER_TRADED_USER)).isEqualByComparingTo("100000.00");
	}

	@Test
	void withdrawing_everything_that_is_available_empties_the_account() throws Exception {
		deposited(NEVER_TRADED_USER, 100000);
		withdrawn(NEVER_TRADED_USER, 100001);

		String filled = withdrawn(NEVER_TRADED_USER, 100000);

		assertThat(reportedAs(filled, "status")).isEqualTo("FILLED");
		assertThat(settledCashOf(NEVER_TRADED_USER)).isEqualByComparingTo("0.00");
		assertThat(availableCashOf(NEVER_TRADED_USER)).isEqualByComparingTo("0.00");
	}

	@Test
	void a_user_with_no_orders_cannot_withdraw_a_single_peso() throws Exception {
		long before = ordersOf(USER_THREE);

		String rejected = withdrawn(USER_THREE, 1);

		assertThat(reportedAs(rejected, "status")).isEqualTo("REJECTED");
		assertThat(reportedAs(rejected, "rejectionReason")).isEqualTo("INSUFFICIENT_FUNDS");
		assertThat(ordersOf(USER_THREE)).isEqualTo(before + 1);
	}

}
