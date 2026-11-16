package com.cocos.trading.service.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class InstrumentMatchesTest {

	private static final SearchLimits LIMITS = new SearchLimits(64, 20, 100);

	@Test
	void fifty_nine_matches_at_twenty_a_page_fill_three_pages() {
		assertThat(searchOf(20, 59).totalPages()).isEqualTo(3);
	}

	@Test
	void a_page_big_enough_to_hold_every_match_is_one_page() {
		assertThat(searchOf(59, 59).totalPages()).isEqualTo(1);
	}

	@Test
	void nothing_matched_fills_no_pages_at_all() {
		assertThat(searchOf(20, 0).totalPages()).isZero();
	}

	@Test
	void a_page_exactly_full_does_not_imply_another_one() {
		assertThat(searchOf(20, 40).totalPages()).isEqualTo(2);
	}

	@Test
	void one_match_over_a_full_page_opens_the_next() {
		assertThat(searchOf(20, 41).totalPages()).isEqualTo(3);
	}

	private static InstrumentMatches searchOf(int size, long totalElements) {
		return new InstrumentMatches(SearchQuery.of("a", LIMITS), List.of(), SearchPage.of(0, size, LIMITS),
				totalElements);
	}

}
