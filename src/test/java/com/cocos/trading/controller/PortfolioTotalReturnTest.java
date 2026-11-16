package com.cocos.trading.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;

class PortfolioTotalReturnTest extends WebTestBase {

	@Test
	void each_long_position_reports_its_return_and_the_short_one_reports_none() throws Exception {
		mvc.perform(get(PORTFOLIO, 1))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.positions[0].ticker").value("BMA"))
			.andExpect(jsonPath("$.positions[0].totalReturnPercent").isEmpty())
			.andExpect(jsonPath("$.positions[1].ticker").value("METR"))
			.andExpect(jsonPath("$.positions[1].totalReturnPercent").value(-8.20))
			.andExpect(jsonPath("$.positions[2].ticker").value("PAMP"))
			.andExpect(jsonPath("$.positions[2].totalReturnPercent").value(-0.45));
	}

	@Test
	@Sql("/db/fixtures/bma-crossing.sql")
	@Sql(scripts = "/db/fixtures/bma-crossing-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_METHOD)
	void a_position_reopened_through_zero_is_based_on_the_crossing_price() throws Exception {
		mvc.perform(get(PORTFOLIO, 1))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.positions[0].ticker").value("BMA"))
			.andExpect(jsonPath("$.positions[0].quantity").value(20))
			.andExpect(jsonPath("$.positions[0].marketValue").value(30056.00))
			.andExpect(jsonPath("$.positions[0].totalReturnPercent").value(0.00));
	}

}
