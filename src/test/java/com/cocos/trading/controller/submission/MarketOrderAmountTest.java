package com.cocos.trading.controller.submission;

import static com.cocos.trading.controller.ApiPaths.PORTFOLIO;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.cocos.trading.controller.SubmissionWebTestBase;

class MarketOrderAmountTest extends SubmissionWebTestBase {

	@Test
	void a_hundred_thousand_pesos_buys_the_hundred_and_eight_shares_that_fit_in_it() throws Exception {
		submit(SEEDED_USER, marketOrderOf(PAMP, "BUY", amountOf("100000.00"))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("FILLED"))
			.andExpect(jsonPath("$.size").value(108))
			.andExpect(jsonPath("$.price").value(925.85))
			.andExpect(jsonPath("$.rejectionReason").value(nullValue()));
	}

	@Test
	void the_unspent_remainder_stays_in_the_account_and_is_not_an_error() throws Exception {
		submit(SEEDED_USER, marketOrderOf(PAMP, "BUY", amountOf("100000.00"))).andExpect(status().isCreated());

		mvc.perform(get(PORTFOLIO, SEEDED_USER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.availableCash").value(527508.20));
	}

	@Test
	void the_whole_available_cash_buys_six_hundred_and_seventy_seven_shares() throws Exception {
		submit(SEEDED_USER, marketOrderOf(PAMP, "BUY", amountOf("627500.00"))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("FILLED"))
			.andExpect(jsonPath("$.size").value(677));
	}

	@Test
	void thirty_thousand_pesos_of_a_forty_share_position_sells_thirty_two() throws Exception {
		submit(SEEDED_USER, marketOrderOf(PAMP, "SELL", amountOf("30000.00"))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("FILLED"))
			.andExpect(jsonPath("$.size").value(32))
			.andExpect(jsonPath("$.price").value(925.85));
	}

	@Test
	void the_sale_leaves_eight_shares_and_the_proceeds_in_the_account() throws Exception {
		submit(SEEDED_USER, marketOrderOf(PAMP, "SELL", amountOf("30000.00"))).andExpect(status().isCreated());

		mvc.perform(get(PORTFOLIO, SEEDED_USER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.availableCash").value(657127.20));
	}

	@Test
	void the_position_is_sellable_to_the_cent() throws Exception {
		submit(SEEDED_USER, marketOrderOf(PAMP, "SELL", amountOf("37034.00"))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("FILLED"))
			.andExpect(jsonPath("$.size").value(40));
	}

}
