package com.cocos.trading.controller.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.cocos.trading.controller.WebTestBase;

class InstrumentSearchPricesTest extends WebTestBase {

	@Test
	void a_result_carries_its_latest_price_and_the_days_move() throws Exception {
		search("pamp").andExpect(status().isOk())
			.andExpect(jsonPath("$.results[0].instrumentId").value(47))
			.andExpect(jsonPath("$.results[0].close").value(925.85))
			.andExpect(jsonPath("$.results[0].dailyReturnPercent").value(0.44));
	}

	@Test
	void an_instrument_with_no_market_data_is_still_returned_with_both_figures_null() throws Exception {
		search("pgr").andExpect(status().isOk())
			.andExpect(jsonPath("$.results[0].instrumentId").value(3))
			.andExpect(jsonPath("$.results[0].ticker").value("PGR"))
			.andExpect(jsonPath("$.results[0].name").value("Phoenix Global Resources"))
			.andExpect(jsonPath("$.results[0].close").isEmpty())
			.andExpect(jsonPath("$.results[0].dailyReturnPercent").isEmpty());
	}

	@Test
	void an_unknown_price_is_never_served_as_zero() throws Exception {
		search("pgr").andExpect(status().isOk())
			.andExpect(jsonPath("$.results[0].close").value(nullValue()))
			.andExpect(jsonPath("$.results[0].dailyReturnPercent").value(nullValue()));
	}

	@Test
	void both_keys_are_always_present_even_when_the_figures_are_unknown() throws Exception {
		String body = search("pgr").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(body).contains("\"close\":null").contains("\"dailyReturnPercent\":null");
	}

	@Test
	void a_flat_day_is_a_genuine_zero_which_is_a_different_answer_from_unknown() throws Exception {
		search("agro").andExpect(status().isOk())
			.andExpect(jsonPath("$.results[0].ticker").value("AGRO"))
			.andExpect(jsonPath("$.results[0].close").value(138.00))
			.andExpect(jsonPath("$.results[0].dailyReturnPercent").value(0.00))
			.andExpect(jsonPath("$.results[0].dailyReturnPercent").isNumber());
	}

}
