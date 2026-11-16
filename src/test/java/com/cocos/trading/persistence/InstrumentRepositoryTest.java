package com.cocos.trading.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import com.cocos.trading.persistence.entity.InstrumentEntity;

class InstrumentRepositoryTest extends PersistenceTestBase {

	private static final int DEFAULT_SIZE = 20;

	private static final int PAMP = 47;

	@Autowired
	private InstrumentRepository instrumentRepository;

	@Test
	void a_ticker_matches_in_any_case() {
		assertThat(idsMatching("pamp")).containsExactly(PAMP);
		assertThat(idsMatching("PAMP")).containsExactly(PAMP);
		assertThat(idsMatching("PaMp")).containsExactly(PAMP);
	}

	@Test
	void a_partial_ticker_matches() {
		assertThat(idsMatching("pam")).containsExactly(PAMP);
	}

	@Test
	void a_query_matching_nothing_returns_no_rows_and_a_total_of_zero() {
		assertThat(pageOf("ZZZZ", 0).getContent()).isEmpty();
		assertThat(pageOf("ZZZZ", 0).getTotalElements()).isZero();
	}

	@Test
	void cash_is_never_a_result() {
		assertThat(tickersMatching("ars")).isEmpty();
		assertThat(tickersMatching("pesos")).isEmpty();
		assertThat(pageOf("ars", 0).getTotalElements()).isZero();
	}

	@Test
	void pattern_metacharacters_are_matched_literally_and_never_return_the_whole_table() {
		assertThat(tickersMatching("%")).isEmpty();
		assertThat(tickersMatching("_")).isEmpty();
		assertThat(tickersMatching("'")).isEmpty();
		assertThat(tickersMatching("\\")).isEmpty();
	}

	@Test
	void a_transposition_matches_nothing_because_matching_is_literal() {
		assertThat(tickersMatching("bnaco")).isEmpty();
	}

	@Test
	void a_name_fragment_returns_the_family_that_shares_it_in_any_case() {
		assertThat(tickersMatching("banco")).containsExactly("BBAR", "BHIP", "BMA", "BPAT");
		assertThat(tickersMatching("BANCO")).containsExactly("BBAR", "BHIP", "BMA", "BPAT");
	}

	@Test
	void an_accented_name_is_reachable_from_a_query_with_the_same_accent() {
		assertThat(tickersMatching("río")).containsExactly("MOLI");
		assertThat(tickersMatching("RÍO")).containsExactly("MOLI");
		assertThat(tickersMatching("compañía")).containsExactly("INTR");
		assertThat(tickersMatching("zorraquín")).containsExactly("GARO");
	}

	@Test
	void an_accented_name_is_not_reachable_from_an_unaccented_query() {
		assertThat(tickersMatching("rio")).containsExactly("BHIP", "OEST", "RICH");
		assertThat(tickersMatching("compania")).isEmpty();
		assertThat(tickersMatching("zorraquin")).isEmpty();
	}

	@Test
	void an_instrument_whose_ticker_and_name_both_match_is_one_row() {
		assertThat(tickersMatching("metr")).containsExactly("METR");
		assertThat(pageOf("metr", 0).getTotalElements()).isEqualTo(1);
	}

	@Test
	void the_matches_come_back_ordered_by_ticker() {
		assertThat(tickersMatching("gr")).containsExactly("AGRO", "GCLA", "GGAL", "GRIM", "LOMA", "MOLA", "OEST", "PGR",
				"SUPV");
	}

	@Test
	void the_pages_of_a_broad_query_neither_repeat_nor_skip_a_row() {
		List<String> first = tickersOnPage("a", 0);
		List<String> second = tickersOnPage("a", 1);
		List<String> third = tickersOnPage("a", 2);

		assertThat(first).hasSize(20);
		assertThat(second).hasSize(20);
		assertThat(third).hasSize(19);
		assertThat(concatenated(first, second, third)).hasSize(59).doesNotHaveDuplicates();
	}

	@Test
	void a_page_past_the_last_one_is_empty_and_the_total_is_unchanged() {
		Page<InstrumentEntity> past = pageOf("a", 3);

		assertThat(past.getContent()).isEmpty();
		assertThat(past.getTotalElements()).isEqualTo(59);
	}

	@Test
	void the_same_query_run_twice_returns_the_same_rows_in_the_same_order() {
		assertThat(tickersOnPage("a", 1)).isEqualTo(tickersOnPage("a", 1));
	}

	private Page<InstrumentEntity> pageOf(String query, int page) {
		return instrumentRepository.searchNonCash(query, page, DEFAULT_SIZE);
	}

	private List<String> tickersOnPage(String query, int page) {
		return pageOf(query, page).map(InstrumentEntity::getTicker).getContent();
	}

	private List<String> tickersMatching(String query) {
		return tickersOnPage(query, 0);
	}

	private List<Integer> idsMatching(String query) {
		return pageOf(query, 0).map(InstrumentEntity::getId).getContent();
	}

	private static List<String> concatenated(List<String> first, List<String> second, List<String> third) {
		return Stream.of(first, second, third).flatMap(List::stream).toList();
	}

}
