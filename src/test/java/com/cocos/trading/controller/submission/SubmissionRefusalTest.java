package com.cocos.trading.controller.submission;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.cocos.trading.controller.SubmissionWebTestBase;

class SubmissionRefusalTest extends SubmissionWebTestBase {

	private static final long UNKNOWN_USER = 99999;

	private static final long UNKNOWN_INSTRUMENT = 9999;

	@Test
	void a_user_that_does_not_exist_is_the_requests_missing_target() throws Exception {
		refused(UNKNOWN_USER, marketOrderOf(PAMP, "BUY", sizeOf(1))).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.title").value("Not Found"))
			.andExpect(jsonPath("$.detail").isString());
	}

	@Test
	void an_instrument_that_does_not_exist_is_named_by_the_body_and_so_is_a_bad_request() throws Exception {
		refused(SEEDED_USER, marketOrderOf(UNKNOWN_INSTRUMENT, "BUY", sizeOf(1))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("No instrument with id " + UNKNOWN_INSTRUMENT));
	}

	@Test
	void cash_is_not_a_tradable_instrument() throws Exception {
		refused(SEEDED_USER, marketOrderOf(ARS, "BUY", sizeOf(10))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Instrument 66 (ARS) is cash, not a tradable instrument"));
	}

	@Test
	void a_market_order_for_an_instrument_that_does_not_quote_has_no_execution_price() throws Exception {
		refused(SEEDED_USER, marketOrderOf(PGR, "BUY", sizeOf(1))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail")
				.value("Instrument 3 (PGR) has no market price, so a MARKET order has no execution price"));
	}

	@Test
	void a_market_order_carrying_a_price_is_refused_rather_than_having_it_ignored() throws Exception {
		refused(SEEDED_USER,
				"{\"instrumentId\": 47, \"type\": \"MARKET\", \"side\": \"BUY\", \"size\": 10, \"price\": 900.00}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("A MARKET order executes at the latest close and takes no price"));
	}

	@Test
	void a_side_that_moves_cash_is_not_an_order_against_the_market() throws Exception {
		refused(SEEDED_USER, marketOrderOf(ARS, "CASH_IN", sizeOf(1000))).andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1", "10.5", "2147483648" })
	void a_size_the_ledger_cannot_hold_is_refused(String size) throws Exception {
		refused(SEEDED_USER, "{\"instrumentId\": 47, \"type\": \"MARKET\", \"side\": \"BUY\", \"size\": " + size + "}")
			.andExpect(status().isBadRequest());
	}

	@Test
	void an_order_states_either_a_size_or_an_amount() throws Exception {
		refused(SEEDED_USER, "{\"instrumentId\": 47, \"type\": \"MARKET\", \"side\": \"BUY\"}")
			.andExpect(status().isBadRequest());
		refused(SEEDED_USER,
				"{\"instrumentId\": 47, \"type\": \"MARKET\", \"side\": \"BUY\", \"size\": 10, \"amount\": 1000.00}")
			.andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = { "{", "", "not json", "{\"instrumentId\": \"PAMP\"}" })
	void a_body_that_cannot_be_read_is_refused(String body) throws Exception {
		refused(SEEDED_USER, body).andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@ValueSource(strings = { "WHATEVER", "CANCEL" })
	void a_type_that_is_not_an_order_type_is_refused(String type) throws Exception {
		refused(SEEDED_USER, "{\"instrumentId\": 47, \"type\": \"" + type + "\", \"side\": \"BUY\", \"size\": 1}")
			.andExpect(status().isBadRequest());
	}

	@Test
	void a_field_that_is_absent_is_not_reported_as_an_unknown_value() throws Exception {
		refused(SEEDED_USER, "{\"instrumentId\": 47, \"side\": \"BUY\", \"size\": 1}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("An order states its type in 'type': MARKET or LIMIT"));
		refused(SEEDED_USER, "{\"instrumentId\": 47, \"type\": \"MARKET\", \"size\": 1}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("An order states its side in 'side': BUY or SELL"));
	}

	@Test
	void a_side_that_is_not_a_side_is_refused() throws Exception {
		refused(SEEDED_USER, marketOrderOf(PAMP, "SIDEWAYS", sizeOf(1))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Unknown side 'SIDEWAYS': an order is BUY or SELL"));
	}

}
