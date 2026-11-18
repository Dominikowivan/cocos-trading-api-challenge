package com.cocos.trading.controller.portfolio;

import static com.cocos.trading.controller.ApiPaths.PORTFOLIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;

import com.jayway.jsonpath.JsonPath;

import com.cocos.trading.controller.WebTestBase;

class PortfolioTotalValueTest extends WebTestBase {

	@Test
	void the_seeded_user_reports_the_account_total() throws Exception {
		mvc.perform(get(PORTFOLIO, 1))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalAccountValue").value(889756.00));
	}

	@ParameterizedTest
	@ValueSource(longs = { 1, 2, 3, 4 })
	void the_response_reconciles_with_itself(long userId) throws Exception {
		String body = mvc.perform(get(PORTFOLIO, userId))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		Object total = JsonPath.read(body, "$.totalAccountValue");
		assertThat(total).as("every seeded user is priced, so no total is suppressed").isNotNull();

		BigDecimal settled = new BigDecimal(JsonPath.read(body, "$.settledCash").toString());
		List<Object> marketValues = JsonPath.read(body, "$.positions[*].marketValue");
		BigDecimal positions = marketValues.stream()
			.map(value -> new BigDecimal(value.toString()))
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		assertThat(new BigDecimal(total.toString())).isEqualByComparingTo(settled.add(positions));
	}

	@Test
	void a_user_who_has_never_traded_reports_zero() throws Exception {
		mvc.perform(get(PORTFOLIO, 2))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalAccountValue").value(0.00));
	}

	@Test
	@Sql("/db/fixtures/unpriced-holding.sql")
	@Sql(scripts = "/db/fixtures/unpriced-holding-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_METHOD)
	void one_unpriced_holding_suppresses_the_total_and_leaves_cash_known() throws Exception {
		mvc.perform(get(PORTFOLIO, 3))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalAccountValue").isEmpty())
			.andExpect(jsonPath("$.settledCash").value(477500.00))
			.andExpect(jsonPath("$.availableCash").value(477500.00))
			.andExpect(jsonPath("$.positions[0].marketValue").isEmpty())
			.andExpect(jsonPath("$.positions[0].totalReturnPercent").isEmpty());
	}

}
