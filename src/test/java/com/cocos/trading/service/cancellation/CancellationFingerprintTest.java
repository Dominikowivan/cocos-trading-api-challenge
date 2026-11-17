package com.cocos.trading.service.cancellation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CancellationFingerprintTest {

	private static final long USER = 1;

	private static final long ORDER = 5;

	@Test
	void the_canonical_form_names_the_operation_first_then_the_user_and_the_order() {
		assertThat(CancellationFingerprint.canonicalFormOf(new CancellationRequest(USER, ORDER)))
			.isEqualTo("operation=ORDER_CANCELLATION;userId=1;orderId=5");
	}

	@Test
	void a_different_user_or_order_is_a_different_request() {
		String cancellation = fingerprintOf(new CancellationRequest(USER, ORDER));

		assertThat(fingerprintOf(new CancellationRequest(2, ORDER))).isNotEqualTo(cancellation);
		assertThat(fingerprintOf(new CancellationRequest(USER, 7))).isNotEqualTo(cancellation);
	}

	@Test
	void no_submission_or_transfer_form_can_equal_a_cancellation_form() {
		assertThat(CancellationFingerprint.canonicalFormOf(new CancellationRequest(USER, ORDER)))
			.startsWith("operation=ORDER_CANCELLATION;")
			.doesNotStartWith("userId=")
			.doesNotStartWith("operation=CASH_TRANSFER");
	}

	@Test
	void the_key_itself_is_not_part_of_the_hash() {
		assertThat(fingerprintOf(new CancellationRequest(USER, ORDER, "cancel-5")))
			.isEqualTo(fingerprintOf(new CancellationRequest(USER, ORDER)));
	}

	@Test
	void the_fingerprint_is_sixty_four_hexadecimal_characters() {
		assertThat(fingerprintOf(new CancellationRequest(USER, ORDER))).containsPattern("^[0-9a-f]{64}$");
	}

	private static String fingerprintOf(CancellationRequest request) {
		return CancellationFingerprint.of(request).hex();
	}

}
