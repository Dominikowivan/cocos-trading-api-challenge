package com.cocos.trading.service.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TransferFingerprintTest {

	private static final long USER = 2;

	@Test
	void the_canonical_form_names_the_operation_first_then_user_side_and_whole_pesos() {
		assertThat(TransferFingerprint.canonicalFormOf(depositOf(USER, "100")))
			.isEqualTo("operation=CASH_TRANSFER;userId=2;side=CASH_IN;amount=100");
	}

	@Test
	void an_amount_written_with_trailing_zeros_is_the_same_request() {
		assertThat(TransferFingerprint.canonicalFormOf(depositOf(USER, "100.00")))
			.isEqualTo(TransferFingerprint.canonicalFormOf(depositOf(USER, "100")));
		assertThat(fingerprintOf(depositOf(USER, "100.00"))).isEqualTo(fingerprintOf(depositOf(USER, "100")));
	}

	@Test
	void a_round_amount_is_written_plainly() {
		assertThat(TransferFingerprint.canonicalFormOf(depositOf(USER, "1000000"))).endsWith("amount=1000000");
	}

	@Test
	void a_different_side_amount_or_user_is_a_different_request() {
		String deposit = fingerprintOf(depositOf(USER, "100"));

		assertThat(fingerprintOf(new TransferRequest(USER, "CASH_OUT", new BigDecimal("100")))).isNotEqualTo(deposit);
		assertThat(fingerprintOf(depositOf(USER, "101"))).isNotEqualTo(deposit);
		assertThat(fingerprintOf(depositOf(3, "100"))).isNotEqualTo(deposit);
	}

	@Test
	void no_order_form_can_equal_a_transfer_form_because_order_forms_begin_with_the_user() {
		assertThat(TransferFingerprint.canonicalFormOf(depositOf(USER, "100"))).startsWith("operation=")
			.doesNotStartWith("userId=");
	}

	@Test
	void the_key_itself_is_not_part_of_the_hash() {
		TransferRequest keyed = new TransferRequest(USER, "CASH_IN", new BigDecimal("100"), Set.of(), "dep-1");

		assertThat(fingerprintOf(keyed)).isEqualTo(fingerprintOf(depositOf(USER, "100")));
	}

	@Test
	void the_fingerprint_is_sixty_four_hexadecimal_characters() {
		assertThat(fingerprintOf(depositOf(USER, "100"))).hasSize(64).containsPattern("^[0-9a-f]{64}$");
	}

	@Test
	void a_fingerprint_matches_its_own_hex_and_no_other() {
		TransferFingerprint fingerprint = TransferFingerprint.of(depositOf(USER, "100"));

		assertThat(fingerprint.matches(fingerprint.hex())).isTrue();
		assertThat(fingerprint.matches(fingerprintOf(depositOf(USER, "101")))).isFalse();
	}

	private static String fingerprintOf(TransferRequest request) {
		return TransferFingerprint.of(request).hex();
	}

	private static TransferRequest depositOf(long userId, String amount) {
		return new TransferRequest(userId, "CASH_IN", new BigDecimal(amount));
	}

}
