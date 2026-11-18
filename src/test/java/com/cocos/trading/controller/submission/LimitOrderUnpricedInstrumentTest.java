package com.cocos.trading.controller.submission;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.cocos.trading.controller.SubmissionWebTestBase;

class LimitOrderUnpricedInstrumentTest extends SubmissionWebTestBase {

	@Test
	void a_market_order_for_an_instrument_that_does_not_quote_has_no_execution_price() throws Exception {
		refused(SEEDED_USER, marketOrderOf(PGR, "BUY", sizeOf(1))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail")
				.value("Instrument 3 (PGR) has no market price, so a MARKET order has no execution price"));
		refused(SEEDED_USER, marketOrderOf(IRCP, "BUY", sizeOf(1))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail")
				.value("Instrument 10 (IRCP) has no market price, so a MARKET order has no execution price"));
	}

	@Test
	void the_same_instrument_is_tradable_with_a_limit_order_because_the_price_came_in_the_request() throws Exception {
		submit(SEEDED_USER, limitOrderOf(PGR, "BUY", sizeOf(1), "100.00")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("NEW"))
			.andExpect(jsonPath("$.ticker").value("PGR"))
			.andExpect(jsonPath("$.price").value(100.00));
	}

	@Test
	void a_second_unpriced_instrument_behaves_the_same_way() throws Exception {
		submit(SEEDED_USER, limitOrderOf(IRCP, "BUY", sizeOf(1), "100.00")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("NEW"))
			.andExpect(jsonPath("$.ticker").value("IRCP"));
	}

	@Test
	void an_unpriced_instrument_is_validated_against_money_like_any_other() throws Exception {
		rejected(SEEDED_USER, limitOrderOf(PGR, "BUY", sizeOf(1000000), "100.00"))
			.andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_FUNDS"));
	}

	@Test
	void a_limit_order_for_an_amount_of_an_unpriced_instrument_derives_its_size_from_the_requested_price()
			throws Exception {
		submit(SEEDED_USER, limitOrderOf(PGR, "BUY", amountOf("1000.00"), "100.00")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("NEW"))
			.andExpect(jsonPath("$.size").value(10));
	}

}
