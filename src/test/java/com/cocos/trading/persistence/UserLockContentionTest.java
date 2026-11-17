package com.cocos.trading.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class UserLockContentionTest extends PersistenceTestBase {

	private static final int SEEDED_ORDERS = 11;

	private static final int SEEDED_USER = 1;

	private static final int ARS = 66;

	private static final Duration DEADLINE = Duration.ofSeconds(10);

	private static final Duration POLL = Duration.ofMillis(20);

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@AfterEach
	void restoreTheSeed() {
		jdbcTemplate.update("DELETE FROM orders WHERE id > ?", SEEDED_ORDERS);
	}

	@Test
	void a_second_transaction_waits_for_the_first_and_then_reads_what_it_committed() throws Exception {
		AtomicLong lockedAt = new AtomicLong();
		AtomicLong ordersSeen = new AtomicLong();
		Thread waiter = waiterFor(lockedAt, ordersSeen);

		long committedAt;
		try (Connection holder = lockedUser()) {
			insertCashInThrough(holder);
			waiter.start();
			await("the waiter blocking on the user's row").atMost(DEADLINE)
				.pollInterval(POLL)
				.until(() -> blockedUserLocks() > 0);
			committedAt = System.nanoTime();
			holder.commit();
		}
		waiter.join();

		assertThat(lockedAt.get()).isGreaterThan(committedAt);
		assertThat(ordersSeen.get()).isEqualTo(SEEDED_ORDERS + 1L);
	}

	private Thread waiterFor(AtomicLong lockedAt, AtomicLong ordersSeen) {
		return new Thread(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			assertThat(userRepository.lockById(SEEDED_USER)).isPresent();
			lockedAt.set(System.nanoTime());
			ordersSeen.set(ordersOf(SEEDED_USER));
		}));
	}

	private long blockedUserLocks() {
		return jdbcTemplate.queryForObject("""
				SELECT count(*) FROM pg_stat_activity
				WHERE wait_event_type = 'Lock' AND query ILIKE '%from users%for%update%'
				""", Long.class);
	}

	private long ordersOf(int userId) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM orders WHERE userid = ?", Long.class, userId);
	}

	private Connection lockedUser() throws SQLException {
		Connection connection = dataSource.getConnection();
		connection.setAutoCommit(false);
		connection.createStatement().execute("SELECT id FROM users WHERE id = " + SEEDED_USER + " FOR UPDATE");
		return connection;
	}

	private static void insertCashInThrough(Connection holder) throws SQLException {
		holder.createStatement()
			.execute("INSERT INTO orders (instrumentId, userId, size, price, type, side, status, datetime) VALUES ("
					+ ARS + ", " + SEEDED_USER + ", 1, 1, 'MARKET', 'CASH_IN', 'FILLED', now())");
	}

}
