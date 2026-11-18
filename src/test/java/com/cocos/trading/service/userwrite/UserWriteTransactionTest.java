package com.cocos.trading.service.userwrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.cocos.trading.TestcontainersConfiguration;
import com.cocos.trading.model.user.UserId;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserWriteTransactionTest {

	private static final UserId SEEDED_USER = new UserId(1);

	private static final KeyedRequest A_KEYED_REQUEST = new KeyedRequest(new IdempotencyKey("outside-1"),
			() -> "0".repeat(64));

	@Autowired
	private UserWrite userWrite;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void outside_a_transaction_it_is_refused_and_does_no_work() {
		AtomicBoolean worked = new AtomicBoolean();

		assertThatIllegalStateException()
			.isThrownBy(() -> userWrite.write(SEEDED_USER, Optional.empty(), userIdColumn -> workDone(worked),
					orderId -> nothingRecorded()))
			.withMessageContaining("transaction");

		assertThat(worked).isFalse();
	}

	@Test
	void outside_a_transaction_a_keyed_request_claims_nothing() {
		assertThatIllegalStateException().isThrownBy(() -> userWrite.write(SEEDED_USER, Optional.of(A_KEYED_REQUEST),
				userIdColumn -> nothingRecorded(), orderId -> nothingRecorded()));

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM order_idempotency", Long.class)).isZero();
	}

	private static WrittenOrder workDone(AtomicBoolean worked) {
		worked.set(true);
		return nothingRecorded();
	}

	private static WrittenOrder nothingRecorded() {
		return () -> 0;
	}

}
