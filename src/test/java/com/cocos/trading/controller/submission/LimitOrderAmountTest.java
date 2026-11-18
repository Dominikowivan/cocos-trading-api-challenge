package com.cocos.trading.controller.submission;

import static com.cocos.trading.controller.ApiPaths.PORTFOLIO;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.cocos.trading.controller.SubmissionWebTestBase;

class LimitOrderAmountTest extends SubmissionWebTestBase {

	@Test
	void a_hundred_thousand_pesos_at_nine_hundred_buys_a_hundred_and_eleven_shares_and_not_a_hundred_and_eight()
			throws Exception {
		submit(SEEDED_USER, limitOrderOf(PAMP, "BUY", amountOf("100000.00"), "900.00")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("NEW"))
			.andExpect(jsonPath("$.size").value(111))
			.andExpect(jsonPath("$.price").value(900.00));
	}

	@Test
	void the_order_reserves_what_its_own_price_commits() throws Exception {
		submit(SEEDED_USER, limitOrderOf(PAMP, "BUY", amountOf("100000.00"), "900.00")).andExpect(status().isCreated());

		mvc.perform(get(PORTFOLIO, SEEDED_USER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.availableCash").value(527600.00))
			.andExpect(jsonPath("$.settledCash").value(753000.00));
	}

	@Test
	void thirty_thousand_pesos_of_a_forty_share_position_at_a_thousand_sells_thirty_and_not_thirty_two()
			throws Exception {
		submit(SEEDED_USER, limitOrderOf(PAMP, "SELL", amountOf("30000.00"), "1000.00")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("NEW"))
			.andExpect(jsonPath("$.size").value(30));
	}

	@Test
	void the_same_sale_at_half_the_price_is_a_recorded_rejection() throws Exception {
		rejected(SEEDED_USER, limitOrderOf(PAMP, "SELL", amountOf("30000.00"), "500.00"))
			.andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_HOLDINGS"))
			.andExpect(jsonPath("$.size").value(0))
			.andExpect(jsonPath("$.price").value(500.00));
	}

	@Test
	void an_amount_that_does_not_reach_one_share_at_the_requested_price_is_a_recorded_rejection() throws Exception {
		rejected(SEEDED_USER, limitOrderOf(PAMP, "BUY", amountOf("800.00"), "900.00"))
			.andExpect(jsonPath("$.rejectionReason").value("SIZE_BELOW_ONE_SHARE"))
			.andExpect(jsonPath("$.size").value(0));
	}

	@Test
	void an_amount_beyond_the_available_cash_is_rejected_whatever_the_requested_price() throws Exception {
		rejected(SEEDED_USER, limitOrderOf(PAMP, "BUY", amountOf("700000.00"), "900.00"))
			.andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_FUNDS"));
	}

}
