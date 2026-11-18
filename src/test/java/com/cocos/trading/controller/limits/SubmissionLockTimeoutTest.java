package com.cocos.trading.controller.limits;

import static com.cocos.trading.controller.ApiPaths.ORDERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

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
import com.cocos.trading.controller.ContractValidationConfiguration;

@SpringBootTest(properties = { "spring.transaction.default-timeout=30s",
		"spring.datasource.hikari.connection-init-sql=SET lock_timeout = '300ms'" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ContractValidationConfiguration.class })
class SubmissionLockTimeoutTest {

	private static final long SEEDED_USER = 1;

	private static final String ONE_PAMP = "{\"instrumentId\": 47, \"type\": \"MARKET\", \"side\": \"BUY\", \"size\": 1}";

	private static final Duration WELL_BELOW_THE_TRANSACTION_DEADLINE = Duration.ofSeconds(10);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void a_submission_waiting_for_the_users_lock_gives_up_at_the_lock_timeout() throws Exception {
		long before = orders();

		try (Connection holder = lockedUser()) {
			Instant start = Instant.now();

			mvc.perform(post(ORDERS, SEEDED_USER).contentType(MediaType.APPLICATION_JSON).content(ONE_PAMP))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("The service is temporarily unavailable, try again later"));

			assertThat(Duration.between(start, Instant.now())).isLessThan(WELL_BELOW_THE_TRANSACTION_DEADLINE);
			holder.rollback();
		}

		assertThat(orders()).isEqualTo(before);
	}

	private long orders() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class);
	}

	private Connection lockedUser() throws SQLException {
		Connection connection = dataSource.getConnection();
		connection.setAutoCommit(false);
		connection.createStatement().execute("SELECT id FROM users WHERE id = " + SEEDED_USER + " FOR UPDATE");
		return connection;
	}

}
