package com.cocos.trading.controller.portfolio;

import static com.cocos.trading.controller.ApiPaths.PORTFOLIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import com.cocos.trading.controller.WebTestBase;

class PortfolioCashTest extends WebTestBase {

	@Test
	void the_seeded_user_reports_settled_and_available_cash() throws Exception {
		mvc.perform(get(PORTFOLIO, 1))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.settledCash").value(753000.00))
			.andExpect(jsonPath("$.availableCash").value(627500.00));
	}

	@Test
	void the_gap_between_the_two_is_what_the_open_buy_limit_orders_commit() throws Exception {
		String body = mvc.perform(get(PORTFOLIO, 1))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		BigDecimal settled = new BigDecimal(JsonPath.read(body, "$.settledCash").toString());
		BigDecimal available = new BigDecimal(JsonPath.read(body, "$.availableCash").toString());

		assertThat(settled.subtract(available)).isEqualByComparingTo("125500.00");
	}

	@Test
	void a_user_who_has_never_traded_reports_zeros_rather_than_nulls() throws Exception {
		mvc.perform(get(PORTFOLIO, 2))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.settledCash").value(0.00))
			.andExpect(jsonPath("$.availableCash").value(0.00))
			.andExpect(jsonPath("$.settledCash").isNumber())
			.andExpect(jsonPath("$.availableCash").isNumber());
	}

}
