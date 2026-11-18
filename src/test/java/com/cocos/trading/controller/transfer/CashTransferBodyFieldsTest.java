package com.cocos.trading.controller.transfer;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.cocos.trading.controller.CashTransferWebTestBase;

class CashTransferBodyFieldsTest extends CashTransferWebTestBase {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "instrumentId | 66", "type | \"MARKET\"", "price | 1", "size | 100",
			"status | \"FILLED\"", "currency | \"ARS\"" })
	void a_field_outside_the_transfers_shape_is_refused_naming_it(String field, String value) throws Exception {
		String body = "{\"side\": \"CASH_IN\", \"amount\": 100, \"" + field + "\": " + value + "}";

		refusedTransfer(NEVER_TRADED_USER, body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail")
				.value("A cash transfer carries only 'side' and 'amount', not '" + field + "'"));
	}

	@Test
	void every_field_outside_the_shape_is_named() throws Exception {
		refusedTransfer(NEVER_TRADED_USER,
				"{\"side\": \"CASH_IN\", \"amount\": 100, \"type\": \"MARKET\", \"price\": 1}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail")
				.value("A cash transfer carries only 'side' and 'amount', not 'price', 'type'"));
	}

}
