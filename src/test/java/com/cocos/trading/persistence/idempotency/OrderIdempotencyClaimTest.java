package com.cocos.trading.persistence.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.cocos.trading.persistence.OrderIdempotencyRepository;
import com.cocos.trading.persistence.PersistenceTestBase;

class OrderIdempotencyClaimTest extends PersistenceTestBase {

	private static final Integer SEEDED_USER = 1;

	private static final Integer ANOTHER_USER = 2;

	private static final Integer SEEDED_ORDER = 1;

	private static final String A_KEY = "7c9e6679-7425-40de-944b-e07fc1f90ae7";

	private static final String A_FINGERPRINT = "a".repeat(64);

	private static final String ANOTHER_FINGERPRINT = "b".repeat(64);

	@Autowired
	private OrderIdempotencyRepository orderIdempotencyRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@AfterEach
	void forgetTheKeys() {
		jdbcTemplate.update("DELETE FROM order_idempotency");
	}

	@Test
	void the_first_caller_claims_the_key_and_the_second_does_not() {
		assertThat(inOneTransaction(() -> claim(SEEDED_USER, A_KEY, A_FINGERPRINT))).isTrue();
		assertThat(inOneTransaction(() -> claim(SEEDED_USER, A_KEY, ANOTHER_FINGERPRINT))).isFalse();
		assertThat(claims()).isEqualTo(1);
	}

	@Test
	void the_claim_keeps_the_fingerprint_the_first_caller_stored() {
		inOneTransaction(() -> claim(SEEDED_USER, A_KEY, A_FINGERPRINT));
		inOneTransaction(() -> claim(SEEDED_USER, A_KEY, ANOTHER_FINGERPRINT));

		assertThat(fingerprintOf(SEEDED_USER, A_KEY)).contains(A_FINGERPRINT);
	}

	@Test
	void the_committed_claim_is_read_back_with_its_order_attached() {
		inOneTransaction(() -> {
			claim(SEEDED_USER, A_KEY, A_FINGERPRINT);
			orderIdempotencyRepository.attachOrderTo(SEEDED_USER, A_KEY, SEEDED_ORDER);
			return null;
		});

		assertThat(orderIdOf(SEEDED_USER, A_KEY)).contains(SEEDED_ORDER);
	}

	@Test
	void a_claim_that_has_not_been_attached_yet_names_no_order() {
		inOneTransaction(() -> claim(SEEDED_USER, A_KEY, A_FINGERPRINT));

		assertThat(orderIdOf(SEEDED_USER, A_KEY)).isEmpty();
	}

	@Test
	void the_same_key_for_two_users_is_two_independent_rows() {
		assertThat(inOneTransaction(() -> claim(SEEDED_USER, A_KEY, A_FINGERPRINT))).isTrue();
		assertThat(inOneTransaction(() -> claim(ANOTHER_USER, A_KEY, ANOTHER_FINGERPRINT))).isTrue();

		assertThat(fingerprintOf(SEEDED_USER, A_KEY)).contains(A_FINGERPRINT);
		assertThat(fingerprintOf(ANOTHER_USER, A_KEY)).contains(ANOTHER_FINGERPRINT);
		assertThat(claims()).isEqualTo(2);
	}

	@Test
	void a_key_nobody_claimed_is_absent() {
		assertThat(fingerprintOf(SEEDED_USER, "never-sent")).isEmpty();
	}

	private Boolean claim(Integer userId, String key, String fingerprint) {
		return orderIdempotencyRepository.claim(userId, key, fingerprint, LocalDateTime.now());
	}

	private Optional<String> fingerprintOf(Integer userId, String key) {
		return inOneTransaction(() -> orderIdempotencyRepository.fingerprintOf(userId, key));
	}

	private Optional<Integer> orderIdOf(Integer userId, String key) {
		return inOneTransaction(() -> orderIdempotencyRepository.orderIdOf(userId, key));
	}

	private long claims() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM order_idempotency", Long.class);
	}

	private <T> T inOneTransaction(Supplier<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> work.get());
	}

}
