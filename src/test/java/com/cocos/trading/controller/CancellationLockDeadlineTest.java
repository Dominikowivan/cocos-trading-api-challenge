package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.cocos.trading.TestcontainersConfiguration;

@SpringBootTest(properties = "spring.transaction.default-timeout=1s")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ContractValidationConfiguration.class })
class CancellationLockDeadlineTest {

	private static final long SEEDED_USER = 1;

	private static final long PENDING_BUY = 5;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void a_cancellation_that_waits_past_the_deadline_answers_503_and_writes_nothing() throws Exception {
		try (Connection holder = lockedUser()) {
			mvc.perform(post("/api/v1/users/{userId}/orders/{orderId}/cancel", SEEDED_USER, PENDING_BUY))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("The service is temporarily unavailable, try again later"));

			holder.rollback();
		}

		assertThat(statusOfThePendingBuy()).isEqualTo("NEW");
		assertThat(historyRowsOfThePendingBuy()).isZero();
	}

	private String statusOfThePendingBuy() {
		return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, PENDING_BUY);
	}

	private long historyRowsOfThePendingBuy() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM order_status_history WHERE order_id = ?", Long.class,
				PENDING_BUY);
	}

	private Connection lockedUser() throws SQLException {
		Connection connection = dataSource.getConnection();
		connection.setAutoCommit(false);
		connection.createStatement().execute("SELECT id FROM users WHERE id = " + SEEDED_USER + " FOR UPDATE");
		return connection;
	}

}
