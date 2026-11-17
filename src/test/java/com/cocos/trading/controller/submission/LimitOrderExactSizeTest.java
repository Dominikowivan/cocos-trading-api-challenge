package com.cocos.trading.controller.submission;

import static com.cocos.trading.controller.ApiPaths.PORTFOLIO;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.cocos.trading.controller.SubmissionWebTestBase;

class LimitOrderExactSizeTest extends SubmissionWebTestBase {

	@Test
	void an_accepted_limit_order_is_born_new_at_the_price_the_caller_sent() throws Exception {
		submit(SEEDED_USER, limitOrderOf(PAMP, "BUY", sizeOf(100), "900.00")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("NEW"))
			.andExpect(jsonPath("$.type").value("LIMIT"))
			.andExpect(jsonPath("$.size").value(100))
			.andExpect(jsonPath("$.price").value(900.00))
			.andExpect(jsonPath("$.rejectionReason").value(nullValue()));
	}

	@Test
	void the_money_a_limit_order_commits_is_reserved_and_not_spent() throws Exception {
		submit(SEEDED_USER, limitOrderOf(PAMP, "BUY", sizeOf(100), "900.00")).andExpect(status().isCreated());

		mvc.perform(get(PORTFOLIO, SEEDED_USER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.availableCash").value(537500.00))
			.andExpect(jsonPath("$.settledCash").value(753000.00));
	}

	@Test
	void a_price_nobody_will_trade_at_is_not_a_rejection_reason() throws Exception {
		submit(SEEDED_USER, limitOrderOf(PAMP, "BUY", sizeOf(100), "2000.00")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("NEW"))
			.andExpect(jsonPath("$.price").value(2000.00));
	}

	@Test
	void a_limit_sale_of_the_whole_position_is_accepted_and_leaves_the_cash_untouched() throws Exception {
		submit(SEEDED_USER, limitOrderOf(PAMP, "SELL", sizeOf(40), "1000.00")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("NEW"))
			.andExpect(jsonPath("$.price").value(1000.00));

		mvc.perform(get(PORTFOLIO, SEEDED_USER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.availableCash").value(627500.00));
	}

	@Test
	void a_position_reserved_by_a_limit_sale_cannot_be_sold_a_second_time() throws Exception {
		submit(SEEDED_USER, limitOrderOf(PAMP, "SELL", sizeOf(40), "1000.00")).andExpect(status().isCreated());

		submit(SEEDED_USER, marketOrderOf(PAMP, "SELL", sizeOf(1))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_HOLDINGS"));
	}

	@Test
	void a_limit_order_beyond_the_available_cash_is_a_recorded_rejection_at_its_own_price() throws Exception {
		rejected(SEEDED_USER, limitOrderOf(PAMP, "BUY", sizeOf(700), "900.00"))
			.andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_FUNDS"))
			.andExpect(jsonPath("$.price").value(900.00));
	}

	@Test
	void a_size_the_close_cannot_fund_is_funded_at_a_lower_requested_price() throws Exception {
		submit(SEEDED_USER, limitOrderOf(PAMP, "BUY", sizeOf(697), "900.00")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("NEW"));
	}

}
