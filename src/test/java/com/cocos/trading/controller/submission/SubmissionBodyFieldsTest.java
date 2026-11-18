package com.cocos.trading.controller.submission;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.cocos.trading.controller.SubmissionWebTestBase;

class SubmissionBodyFieldsTest extends SubmissionWebTestBase {

	@Test
	void a_status_in_the_body_does_not_decide_the_outcome() throws Exception {
		String claimingToBeFilled = "{\"instrumentId\": 47, \"type\": \"LIMIT\", \"side\": \"BUY\", \"size\": 10,"
				+ " \"price\": 900.00, \"status\": \"FILLED\"}";

		submit(SEEDED_USER, claimingToBeFilled).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("NEW"));
	}

	@Test
	void a_cancel_flag_in_the_body_does_nothing_because_cancelling_is_another_endpoint() throws Exception {
		String carryingACancelFlag = "{\"instrumentId\": 47, \"type\": \"LIMIT\", \"side\": \"BUY\", \"size\": 10,"
				+ " \"price\": 900.00, \"cancel\": true}";

		submit(SEEDED_USER, carryingACancelFlag).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("NEW"));
	}

	@Test
	void a_price_with_more_precision_than_money_has_is_reported_at_the_money_scale() throws Exception {
		submit(SEEDED_USER, limitOrderOf(PAMP, "BUY", sizeOf(10), "900.126")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("NEW"))
			.andExpect(jsonPath("$.price").value(900.13));
	}

	@Test
	void an_amount_with_more_precision_than_money_has_derives_from_the_scaled_figure() throws Exception {
		submit(SEEDED_USER, marketOrderOf(PAMP, "BUY", amountOf("100000.126"))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("FILLED"))
			.andExpect(jsonPath("$.size").value(108))
			.andExpect(jsonPath("$.price").value(925.85));
	}

}
