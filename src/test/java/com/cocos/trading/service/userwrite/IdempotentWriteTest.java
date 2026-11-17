package com.cocos.trading.service.userwrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.cocos.trading.TestcontainersConfiguration;
import com.cocos.trading.model.user.UserId;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IdempotentWriteTest {

	private static final UserId SEEDED_USER = new UserId(1);

	private static final int A_SEEDED_ORDER = 1;

	@Autowired
	private IdempotentWrite idempotentWrite;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void forgetTheKeys() {
		jdbcTemplate.update("DELETE FROM order_idempotency");
	}

	@Test
	void used_without_the_user_lock_the_first_request_works_and_the_retry_replays_it() {
		AtomicInteger worked = new AtomicInteger();
		KeyedRequest request = keyed("k-alone", "a");

		long first = onceInATransaction(request, worked);
		long retry = onceInATransaction(request, worked);

		assertThat(worked).hasValue(1);
		assertThat(retry).isEqualTo(first).isEqualTo(A_SEEDED_ORDER);
	}

	@Test
	void the_same_key_with_another_fingerprint_is_a_conflict() {
		onceInATransaction(keyed("k-conflict", "a"), new AtomicInteger());

		assertThatThrownBy(() -> onceInATransaction(keyed("k-conflict", "b"), new AtomicInteger()))
			.isInstanceOf(IdempotencyConflictException.class);
	}

	@Test
	void outside_a_transaction_it_is_refused_and_claims_nothing() {
		AtomicInteger worked = new AtomicInteger();

		assertThatIllegalStateException()
			.isThrownBy(() -> idempotentWrite.once(SEEDED_USER, keyed("k-outside", "a"), () -> appended(worked),
					orderId -> () -> orderId))
			.withMessageContaining("transaction");

		assertThat(worked).hasValue(0);
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM order_idempotency", Long.class)).isZero();
	}

	private long onceInATransaction(KeyedRequest request, AtomicInteger worked) {
		return new TransactionTemplate(transactionManager).execute(
				status -> idempotentWrite.once(SEEDED_USER, request, () -> appended(worked), orderId -> () -> orderId)
					.orderId());
	}

	private static WrittenOrder appended(AtomicInteger worked) {
		worked.incrementAndGet();
		return () -> A_SEEDED_ORDER;
	}

	private static KeyedRequest keyed(String key, String fingerprint) {
		return new KeyedRequest(new IdempotencyKey(key), () -> fingerprint.repeat(64));
	}

}
