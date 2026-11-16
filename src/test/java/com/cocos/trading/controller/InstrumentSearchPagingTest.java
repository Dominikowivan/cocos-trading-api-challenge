package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.cocos.trading.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

class InstrumentSearchPagingTest extends WebTestBase {

	@Test
	void the_matches_come_ordered_by_ticker() throws Exception {
		assertThat(tickersOf(search("gr"))).containsExactly("AGRO", "GCLA", "GGAL", "GRIM", "LOMA", "MOLA", "OEST",
				"PGR", "SUPV");
	}

	@Test
	void an_instrument_matching_on_both_fields_is_counted_once() throws Exception {
		search("metr").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void a_broad_query_reports_the_default_size_the_total_and_the_pages() throws Exception {
		search("a").andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(59))
			.andExpect(jsonPath("$.totalPages").value(3))
			.andExpect(jsonPath("$.results.length()").value(20));
	}

	@Test
	void walking_the_pages_returns_every_match_exactly_once() throws Exception {
		List<String> first = tickersOf(page("a", 0));
		List<String> second = tickersOf(page("a", 1));
		List<String> third = tickersOf(page("a", 2));

		assertThat(first).hasSize(20);
		assertThat(second).hasSize(20);
		assertThat(third).hasSize(19);
		assertThat(first).last().isEqualTo("CVH");
		assertThat(second).first().isEqualTo("DGCU2");
		assertThat(third).last().isEqualTo("YPFD");
	}

	@Test
	void a_page_past_the_last_one_is_empty_and_still_reports_the_real_total() throws Exception {
		page("a", 3).andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(3))
			.andExpect(jsonPath("$.results").isEmpty())
			.andExpect(jsonPath("$.totalElements").value(59))
			.andExpect(jsonPath("$.totalPages").value(3));
	}

	@Test
	void a_caller_can_ask_for_the_whole_result_set_in_one_page() throws Exception {
		mvc.perform(get(INSTRUMENT_SEARCH).param("query", "a").param("size", "59"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.size").value(59))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.results.length()").value(59));
	}

	@Test
	void a_size_above_the_configured_maximum_is_clamped_and_the_response_says_so() throws Exception {
		mvc.perform(get(INSTRUMENT_SEARCH).param("query", "a").param("size", "500"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.size").value(100))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.results.length()").value(59));
	}

	@Test
	void a_size_of_zero_falls_back_to_the_default() throws Exception {
		mvc.perform(get(INSTRUMENT_SEARCH).param("query", "a").param("size", "0"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void a_negative_page_is_read_as_the_first_page() throws Exception {
		mvc.perform(get(INSTRUMENT_SEARCH).param("query", "a").param("page", "-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.results[0].ticker").value("AGRO"));
	}

	@Test
	void a_sort_sent_by_the_caller_is_ignored_and_the_order_stays_ticker_then_id() throws Exception {
		List<String> sortedByNameDescending = tickersOf(
				mvc.perform(get(INSTRUMENT_SEARCH).param("query", "gr").param("sort", "name,desc")));

		assertThat(sortedByNameDescending).isEqualTo(tickersOf(search("gr")));
	}

	@Test
	void a_sort_on_a_field_that_does_not_exist_is_ignored_rather_than_failing() throws Exception {
		mvc.perform(get(INSTRUMENT_SEARCH).param("query", "gr").param("sort", "nonsense"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(9));
	}

	@Test
	void two_identical_requests_return_identical_bodies() throws Exception {
		assertThat(bodyOf(search("a"))).isEqualTo(bodyOf(search("a")));
	}

	private ResultActions page(String query, int page) throws Exception {
		return mvc.perform(get(INSTRUMENT_SEARCH).param("query", query).param("page", Integer.toString(page)));
	}

	private static List<String> tickersOf(ResultActions response) throws Exception {
		return JsonPath.read(bodyOf(response), "$.results[*].ticker");
	}

	@Nested
	@SpringBootTest(properties = "spring.data.web.pageable.default-page-size=5")
	@AutoConfigureMockMvc
	@Import({ TestcontainersConfiguration.class, ContractValidationConfiguration.class })
	class WithAConfiguredDefaultPageSize {

		@Autowired
		private MockMvc configured;

		@Test
		void an_absent_size_is_filled_in_from_the_deployments_setting() throws Exception {
			configured.perform(get(INSTRUMENT_SEARCH).param("query", "a"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(5))
				.andExpect(jsonPath("$.totalElements").value(59))
				.andExpect(jsonPath("$.totalPages").value(12))
				.andExpect(jsonPath("$.results.length()").value(5));
		}

	}

}
