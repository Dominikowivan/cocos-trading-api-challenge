package com.cocos.trading.controller.portfolio;

import static com.cocos.trading.controller.ApiPaths.PORTFOLIO;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.cocos.trading.controller.WebTestBase;

class PortfolioQuantitiesTest extends WebTestBase {

	@Test
	void the_seeded_user_reports_one_position_per_held_instrument_in_ticker_order() throws Exception {
		mvc.perform(get(PORTFOLIO, 1))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.userId").value(1))
			.andExpect(jsonPath("$.positions.length()").value(3))
			.andExpect(jsonPath("$.positions[*].ticker").value(contains("BMA", "METR", "PAMP")))
			.andExpect(jsonPath("$.positions[0].instrumentId").value(31))
			.andExpect(jsonPath("$.positions[0].name").value("Banco Macro S.A."))
			.andExpect(jsonPath("$.positions[0].quantity").value(-10))
			.andExpect(jsonPath("$.positions[1].quantity").value(500))
			.andExpect(jsonPath("$.positions[2].quantity").value(40));
	}

	@Test
	void pesos_are_never_reported_as_a_position() throws Exception {
		mvc.perform(get(PORTFOLIO, 1))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.positions[?(@.ticker == 'ARS')]").isEmpty());
	}

	@Test
	void a_user_who_has_never_traded_reports_an_empty_position_list() throws Exception {
		mvc.perform(get(PORTFOLIO, 2))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").value(2))
			.andExpect(jsonPath("$.positions").isEmpty());
	}

	@Test
	void an_unknown_user_is_a_problem_detail_with_status_404() throws Exception {
		mvc.perform(get(PORTFOLIO, 999))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.title").value("Not Found"))
			.andExpect(jsonPath("$.detail").value("User 999 does not exist"));
	}

	@Test
	void a_malformed_user_id_is_a_problem_detail_with_status_400_naming_the_id() throws Exception {
		mvc.perform(get(PORTFOLIO, "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.title").value("Bad Request"))
			.andExpect(jsonPath("$.detail").value(containsString("abc")));
	}

}
