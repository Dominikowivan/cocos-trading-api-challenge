package com.cocos.trading.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;

class PortfolioMarketValueTest extends WebTestBase {

	@Test
	void every_held_instrument_reports_quantity_times_its_own_latest_close() throws Exception {
		mvc.perform(get(PORTFOLIO, 1))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.positions[0].ticker").value("BMA"))
			.andExpect(jsonPath("$.positions[0].marketValue").value(-15028.00))
			.andExpect(jsonPath("$.positions[1].ticker").value("METR"))
			.andExpect(jsonPath("$.positions[1].marketValue").value(114750.00))
			.andExpect(jsonPath("$.positions[2].ticker").value("PAMP"))
			.andExpect(jsonPath("$.positions[2].marketValue").value(37034.00));
	}

	@Test
	@Sql("/db/fixtures/unpriced-holding.sql")
	@Sql(scripts = "/db/fixtures/unpriced-holding-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_METHOD)
	void an_unpriced_holding_is_listed_with_its_quantity_and_a_null_market_value() throws Exception {
		mvc.perform(get(PORTFOLIO, 3))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.positions.length()").value(1))
			.andExpect(jsonPath("$.positions[0].ticker").value("PGR"))
			.andExpect(jsonPath("$.positions[0].quantity").value(25))
			.andExpect(jsonPath("$.positions[0].marketValue").isEmpty())
			.andExpect(jsonPath("$.positions[0].totalReturnPercent").isEmpty())
			.andExpect(jsonPath("$.settledCash").value(477500.00))
			.andExpect(jsonPath("$.availableCash").value(477500.00));
	}

	@Test
	void the_market_value_key_is_always_present() throws Exception {
		mvc.perform(get(PORTFOLIO, 1))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.positions[0]").exists())
			.andExpect(jsonPath("$.positions[0].marketValue").exists());
	}

}
