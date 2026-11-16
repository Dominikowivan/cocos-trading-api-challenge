package com.cocos.trading.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SearchPageTest {

	private static final SearchLimits LIMITS = new SearchLimits(64, 20, 100);

	@Test
	void an_absent_size_takes_the_configured_default() {
		SearchPage first = SearchPage.of(0, null, LIMITS);

		assertThat(first.number()).isZero();
		assertThat(first.size()).isEqualTo(20);
	}

	@Test
	void a_requested_page_carries_what_was_asked_for() {
		SearchPage second = SearchPage.of(1, 20, LIMITS);

		assertThat(second.number()).isEqualTo(1);
		assertThat(second.size()).isEqualTo(20);
	}

	@Test
	void a_negative_page_is_rejected() {
		assertThatThrownBy(() -> SearchPage.of(-1, 20, LIMITS)).isInstanceOf(InvalidSearchRequestException.class)
			.hasMessage("The page number must not be negative");
	}

	@Test
	void a_size_of_zero_is_rejected_and_the_message_names_the_maximum_in_force() {
		assertThatThrownBy(() -> SearchPage.of(0, 0, LIMITS)).isInstanceOf(InvalidSearchRequestException.class)
			.hasMessage("The page size must be between 1 and 100");
	}

	@Test
	void a_size_one_above_the_configured_maximum_is_rejected() {
		assertThatThrownBy(() -> SearchPage.of(0, 101, LIMITS)).isInstanceOf(InvalidSearchRequestException.class)
			.hasMessage("The page size must be between 1 and 100");
	}

	@Test
	void a_size_exactly_at_the_maximum_is_accepted() {
		assertThat(SearchPage.of(0, 100, LIMITS).size()).isEqualTo(100);
	}

	@Test
	void a_size_of_one_is_accepted() {
		assertThat(SearchPage.of(0, 1, LIMITS).size()).isEqualTo(1);
	}

	@Test
	void an_out_of_range_size_is_never_clamped_to_the_maximum() {
		assertThatThrownBy(() -> SearchPage.of(0, 500, LIMITS)).isInstanceOf(InvalidSearchRequestException.class);
	}

	@Test
	void a_negative_page_is_never_clamped_to_the_first_page() {
		assertThatThrownBy(() -> SearchPage.of(-5, 20, LIMITS)).isInstanceOf(InvalidSearchRequestException.class);
	}

	@Test
	void the_maximum_is_the_configured_one_not_a_constant() {
		SearchLimits smaller = new SearchLimits(64, 5, 10);

		assertThat(SearchPage.of(0, 10, smaller).size()).isEqualTo(10);
		assertThatThrownBy(() -> SearchPage.of(0, 11, smaller)).isInstanceOf(InvalidSearchRequestException.class)
			.hasMessage("The page size must be between 1 and 10");
	}

}
