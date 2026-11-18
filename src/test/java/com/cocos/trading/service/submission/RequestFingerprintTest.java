package com.cocos.trading.service.submission;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RequestFingerprintTest {

	private static final long SEEDED_USER = 1;

	private static final long PAMP = 47;

	@Test
	void the_same_request_hashes_identically() {
		assertThat(fingerprintOf(marketBuyOf("100"))).isEqualTo(fingerprintOf(marketBuyOf("100")));
	}

	@Test
	void a_figure_written_with_other_trailing_zeros_is_the_same_request() {
		assertThat(fingerprintOf(limitBuyOf("100", "900"))).isEqualTo(fingerprintOf(limitBuyOf("100.00", "900.00")));
	}

	@Test
	void a_different_figure_is_a_different_request() {
		assertThat(fingerprintOf(marketBuyOf("100"))).isNotEqualTo(fingerprintOf(marketBuyOf("101")));
	}

	@Test
	void a_different_side_is_a_different_request() {
		assertThat(
				fingerprintOf(new SubmissionRequest(SEEDED_USER, PAMP, "MARKET", "SELL", BigDecimal.TEN, null, null)))
			.isNotEqualTo(fingerprintOf(
					new SubmissionRequest(SEEDED_USER, PAMP, "MARKET", "BUY", BigDecimal.TEN, null, null)));
	}

	@Test
	void the_same_body_sent_by_another_user_is_a_different_request() {
		assertThat(fingerprintOf(marketBuyOf("100"))).isNotEqualTo(
				fingerprintOf(new SubmissionRequest(2, PAMP, "MARKET", "BUY", new BigDecimal("100"), null, null)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "7c9e6679-7425-40de-944b-e07fc1f90ae7", "another-key-entirely" })
	void the_key_itself_is_not_part_of_the_hash(String key) {
		SubmissionRequest keyed = new SubmissionRequest(SEEDED_USER, PAMP, "MARKET", "BUY", new BigDecimal("100"), null,
				null, key);

		assertThat(fingerprintOf(keyed)).isEqualTo(fingerprintOf(marketBuyOf("100")));
	}

	@Test
	void the_fingerprint_is_sixty_four_hexadecimal_characters() {
		String hex = RequestFingerprint.of(marketBuyOf("100")).hex();

		assertThat(hex).hasSize(64).containsPattern("^[0-9a-f]{64}$");
	}

	@Test
	void a_fingerprint_matches_the_hex_it_is_stored_as() {
		RequestFingerprint fingerprint = RequestFingerprint.of(marketBuyOf("100"));

		assertThat(fingerprint.matches(fingerprint.hex())).isTrue();
		assertThat(fingerprint.matches(RequestFingerprint.of(marketBuyOf("101")).hex())).isFalse();
	}

	@Test
	void an_absent_field_is_not_the_same_as_a_field_that_was_sent() {
		SubmissionRequest bySize = new SubmissionRequest(SEEDED_USER, PAMP, "MARKET", "BUY", new BigDecimal("100"),
				null, null);
		SubmissionRequest byAmount = new SubmissionRequest(SEEDED_USER, PAMP, "MARKET", "BUY", null,
				new BigDecimal("100"), null);

		assertThat(fingerprintOf(bySize)).isNotEqualTo(fingerprintOf(byAmount));
	}

	private static RequestFingerprint fingerprintOf(SubmissionRequest request) {
		return RequestFingerprint.of(request);
	}

	private static SubmissionRequest marketBuyOf(String size) {
		return new SubmissionRequest(SEEDED_USER, PAMP, "MARKET", "BUY", new BigDecimal(size), null, null);
	}

	private static SubmissionRequest limitBuyOf(String size, String price) {
		return new SubmissionRequest(SEEDED_USER, PAMP, "LIMIT", "BUY", new BigDecimal(size), null,
				new BigDecimal(price));
	}

}
