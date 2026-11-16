package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class InstrumentSearchTickerTest extends WebTestBase {

	@Test
	void an_exact_ticker_returns_the_instrument_with_the_id_the_order_endpoint_consumes() throws Exception {
		search("PAMP").andExpect(status().isOk())
			.andExpect(jsonPath("$.query").value("PAMP"))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.results.length()").value(1))
			.andExpect(jsonPath("$.results[0].instrumentId").value(47))
			.andExpect(jsonPath("$.results[0].ticker").value("PAMP"))
			.andExpect(jsonPath("$.results[0].name").value("Pampa Holding S.A."))
			.andExpect(jsonPath("$.results[0].type").value("ACCIONES"));
	}

	@Test
	void the_same_ticker_in_lower_case_returns_the_same_instrument() throws Exception {
		search("pamp").andExpect(status().isOk())
			.andExpect(jsonPath("$.query").value("pamp"))
			.andExpect(jsonPath("$.results[0].instrumentId").value(47));
	}

	@Test
	void a_partial_ticker_returns_the_same_instrument() throws Exception {
		search("pam").andExpect(status().isOk())
			.andExpect(jsonPath("$.query").value("pam"))
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.results[0].instrumentId").value(47));
	}

	@Test
	void a_query_with_surrounding_whitespace_returns_the_body_of_the_trimmed_one() throws Exception {
		assertThat(bodyOf(search("  pamp  "))).isEqualTo(bodyOf(search("pamp")));
	}

	@Test
	void a_query_that_matches_nothing_is_a_success_with_an_empty_page() throws Exception {
		search("ZZZZ").andExpect(status().isOk())
			.andExpect(jsonPath("$.results").isEmpty())
			.andExpect(jsonPath("$.totalElements").value(0))
			.andExpect(jsonPath("$.totalPages").value(0));
	}

	@Test
	void cash_is_never_a_result() throws Exception {
		search("ars").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		search("pesos").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void a_whitespace_only_query_is_rejected() throws Exception {
		search("   ").andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.detail").value("The search query must not be blank"));
	}

	@Test
	void a_missing_query_parameter_is_rejected() throws Exception {
		mvc.perform(get(INSTRUMENT_SEARCH))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.detail").value("Required request parameter 'query' is not present"));
	}

	@Test
	void a_query_one_character_over_the_configured_maximum_is_rejected() throws Exception {
		search("x".repeat(65)).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.detail").value("The search query must be at most 64 characters"));
	}

	@Test
	void a_query_well_under_the_maximum_is_served_even_when_it_matches_nothing() throws Exception {
		search("x".repeat(50)).andExpect(status().isOk())
			.andExpect(jsonPath("$.results").isEmpty())
			.andExpect(jsonPath("$.totalElements").value(0));
	}

}
