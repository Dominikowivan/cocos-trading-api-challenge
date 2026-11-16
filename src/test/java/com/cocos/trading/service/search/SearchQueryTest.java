package com.cocos.trading.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SearchQueryTest {

	private static final SearchLimits LIMITS = new SearchLimits(64);

	@Test
	void surrounding_whitespace_is_trimmed() {
		assertThat(SearchQuery.of("  pamp  ", LIMITS).text()).isEqualTo("pamp");
	}

	@Test
	void an_already_trimmed_query_is_left_alone() {
		assertThat(SearchQuery.of("pamp", LIMITS).text()).isEqualTo("pamp");
	}

	@Test
	void null_is_rejected() {
		assertThatThrownBy(() -> SearchQuery.of(null, LIMITS)).isInstanceOf(InvalidSearchRequestException.class)
			.hasMessage("The search query must not be blank");
	}

	@Test
	void an_empty_query_is_rejected() {
		assertThatThrownBy(() -> SearchQuery.of("", LIMITS)).isInstanceOf(InvalidSearchRequestException.class)
			.hasMessage("The search query must not be blank");
	}

	@Test
	void a_whitespace_only_query_is_rejected_because_it_trims_to_nothing() {
		assertThatThrownBy(() -> SearchQuery.of("   ", LIMITS)).isInstanceOf(InvalidSearchRequestException.class)
			.hasMessage("The search query must not be blank");
	}

	@Test
	void one_character_over_the_configured_maximum_is_rejected_and_the_message_names_it() {
		String tooLong = "x".repeat(65);

		assertThatThrownBy(() -> SearchQuery.of(tooLong, LIMITS)).isInstanceOf(InvalidSearchRequestException.class)
			.hasMessage("The search query must be at most 64 characters");
	}

	@Test
	void one_character_under_the_configured_maximum_is_accepted() {
		assertThat(SearchQuery.of("x".repeat(63), LIMITS).text()).hasSize(63);
	}

	@Test
	void exactly_the_configured_maximum_is_accepted() {
		assertThat(SearchQuery.of("x".repeat(64), LIMITS).text()).hasSize(64);
	}

	@Test
	void the_maximum_is_the_configured_one_not_a_constant() {
		SearchLimits shorter = new SearchLimits(10);
		String tooLong = "x".repeat(11);

		assertThatThrownBy(() -> SearchQuery.of(tooLong, shorter)).isInstanceOf(InvalidSearchRequestException.class)
			.hasMessage("The search query must be at most 10 characters");
	}

	@Test
	void the_length_is_measured_after_trimming() {
		assertThat(SearchQuery.of("  " + "x".repeat(64) + "  ", LIMITS).text()).hasSize(64);
	}

	@Test
	void the_text_is_left_unfolded_because_folding_is_sqls_job() {
		assertThat(SearchQuery.of("  Compañía  ", LIMITS).text()).isEqualTo("Compañía");
		assertThat(SearchQuery.of("PAMP", LIMITS).text()).isEqualTo("PAMP");
	}

}
