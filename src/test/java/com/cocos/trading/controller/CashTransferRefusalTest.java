package com.cocos.trading.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class CashTransferRefusalTest extends CashTransferWebTestBase {

	@ParameterizedTest
	@ValueSource(strings = { "BUY", "SELL", "SIDEWAYS" })
	void a_side_that_is_not_a_transfer_is_refused_naming_the_accepted_ones(String side) throws Exception {
		refusedTransfer(NEVER_TRADED_USER, transferOf(side, "100")).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(containsString("CASH_IN or CASH_OUT")));
	}

	@Test
	void a_missing_side_is_refused() throws Exception {
		refusedTransfer(NEVER_TRADED_USER, "{\"amount\": 100}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("'side'")));
	}

	@Test
	void a_missing_amount_is_refused() throws Exception {
		refusedTransfer(NEVER_TRADED_USER, "{\"side\": \"CASH_IN\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("'amount'")));
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-5", "2147483648" })
	void an_amount_outside_the_ledgers_bounds_is_refused(String amount) throws Exception {
		refusedTransfer(NEVER_TRADED_USER, transferOf("CASH_IN", amount)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(containsString("between 1 and 2147483647")));
	}

	@Test
	void centavos_are_refused_rather_than_rounded() throws Exception {
		refusedTransfer(NEVER_TRADED_USER, transferOf("CASH_IN", "100.50")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("A cash transfer is a whole number of pesos, and 100.50 is not"));
	}

	@Test
	void a_whole_amount_written_with_decimals_is_accepted_as_whole_pesos() throws Exception {
		transfer(NEVER_TRADED_USER, transferOf("CASH_IN", "100.00")).andExpect(status().isCreated())
			.andExpect(content().string(containsString("\"amount\":100.00")));
	}

	@Test
	void an_unknown_user_with_a_valid_body_is_the_missing_target() throws Exception {
		refusedTransfer(UNKNOWN_USER, depositOf(100)).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("User 999 does not exist"));
	}

	@Test
	void the_body_is_judged_before_the_user_is_looked_up() throws Exception {
		refusedTransfer(UNKNOWN_USER, "{\"side\": \"BUY\"}").andExpect(status().isBadRequest());
	}

	@Test
	void an_unreadable_body_is_refused() throws Exception {
		refusedTransfer(NEVER_TRADED_USER, "{\"side\": \"CASH_IN\", \"amount\": ").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("The request body is not readable JSON of the expected shape"));
	}

	@Test
	void an_amount_that_is_not_a_number_is_refused() throws Exception {
		refusedTransfer(NEVER_TRADED_USER, transferOf("CASH_IN", "\"lots\"")).andExpect(status().isBadRequest());
	}

}
