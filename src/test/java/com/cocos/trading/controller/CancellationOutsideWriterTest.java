package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CancellationOutsideWriterTest extends CancellationWebTestBase {

	private static final Duration DEADLINE = Duration.ofSeconds(10);

	private static final Duration POLL = Duration.ofMillis(20);

	@Autowired
	private DataSource dataSource;

	@Test
	void an_order_filled_by_a_writer_outside_the_users_lock_is_not_cancelled() throws Exception {
		CompletableFuture<Integer> cancellation;
		try (Connection filler = filledButNotCommitted(PENDING_BUY)) {
			cancellation = CompletableFuture.supplyAsync(() -> answerTo(SEEDED_USER, PENDING_BUY));
			awaitAnUpdateBlockedOnTheOrderRow();
			filler.commit();
		}

		assertThat(cancellation.get(DEADLINE.toSeconds(), TimeUnit.SECONDS)).isEqualTo(409);
		assertThat(statusOf(PENDING_BUY)).isEqualTo("FILLED");
		assertThat(historyOf(PENDING_BUY)).isEmpty();
	}

	private Connection filledButNotCommitted(long orderId) throws SQLException {
		Connection connection = dataSource.getConnection();
		connection.setAutoCommit(false);
		connection.createStatement().execute("UPDATE orders SET status = 'FILLED' WHERE id = " + orderId);
		return connection;
	}

	private void awaitAnUpdateBlockedOnTheOrderRow() {
		await("the cancellation reaching the guarded update").atMost(DEADLINE)
			.pollInterval(POLL)
			.until(() -> blockedOrderUpdates() > 0);
	}

	private long blockedOrderUpdates() {
		return jdbc().queryForObject("""
				SELECT count(*) FROM pg_stat_activity
				WHERE wait_event_type = 'Lock' AND query ILIKE 'update orders%'
				""", Long.class);
	}

	private int answerTo(long userId, long orderId) {
		try {
			return cancel(userId, orderId).andReturn().getResponse().getStatus();
		}
		catch (Exception failed) {
			throw new IllegalStateException("the cancellation did not answer", failed);
		}
	}

}
