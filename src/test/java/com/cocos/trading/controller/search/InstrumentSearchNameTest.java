package com.cocos.trading.controller.search;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.cocos.trading.controller.WebTestBase;

class InstrumentSearchNameTest extends WebTestBase {

	@ParameterizedTest
	@ValueSource(strings = { "banco", "BANCO" })
	void one_name_fragment_in_any_case_returns_the_whole_family(String query) throws Exception {
		search(query).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(4))
			.andExpect(jsonPath("$.results[*].ticker").value(contains("BBAR", "BHIP", "BMA", "BPAT")));
	}

	@Test
	void an_accented_query_reaches_the_accented_name() throws Exception {
		search("compañía").andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.results[0].ticker").value("INTR"))
			.andExpect(jsonPath("$.results[0].name").value("Compañía Introductora de Buenos Aires S.A."));
	}

	@Test
	void an_unaccented_query_does_not_reach_an_accented_name() throws Exception {
		search("rio").andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.results[*].ticker").value(not(hasItem("MOLI"))));
	}

	@Test
	void an_accented_name_is_always_reachable_by_its_ticker() throws Exception {
		search("moli").andExpect(status().isOk())
			.andExpect(jsonPath("$.results[*].name").value(hasItem("Molinos Río de la Plata")));
	}

	@Test
	void a_transposition_is_a_success_with_an_empty_page_because_matching_is_literal() throws Exception {
		search("bnaco").andExpect(status().isOk())
			.andExpect(jsonPath("$.results").isEmpty())
			.andExpect(jsonPath("$.totalElements").value(0));
	}

}
