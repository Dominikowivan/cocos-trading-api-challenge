package com.cocos.trading.controller;

import static com.cocos.trading.controller.ApiPaths.ORDERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

public abstract class SubmissionWebTestBase extends WebTestBase {

	protected static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	protected static final long SEEDED_USER = 1;

	protected static final long NEVER_TRADED_USER = 2;

	protected static final long PAMP = 47;

	protected static final long BMA = 31;

	protected static final long PGR = 3;

	protected static final long ARS = 66;

	protected static final long IRCP = 10;

	private static final int SEEDED_ORDERS = 11;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private DataSource dataSource;

	@AfterEach
	void restoreTheSeed() {
		jdbcTemplate.update("DELETE FROM order_idempotency");
		jdbcTemplate.update("DELETE FROM order_status_history WHERE order_id > ?", SEEDED_ORDERS);
		jdbcTemplate.update("DELETE FROM orders WHERE id > ?", SEEDED_ORDERS);
	}

	protected ResultActions submit(long userId, String body) throws Exception {
		return mvc.perform(post(ORDERS, userId).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	protected ResultActions submit(long userId, String body, String idempotencyKey) throws Exception {
		return mvc.perform(post(ORDERS, userId).contentType(MediaType.APPLICATION_JSON)
			.header(IDEMPOTENCY_KEY, idempotencyKey)
			.content(body));
	}

	protected ResultActions refused(long userId, String body) throws Exception {
		long before = ordersInTheLedger();

		ResultActions response = submit(userId, body);

		assertThat(ordersInTheLedger()).isEqualTo(before);
		return response;
	}

	protected ResultActions refused(long userId, String body, String idempotencyKey) throws Exception {
		long before = ordersInTheLedger();

		ResultActions response = submit(userId, body, idempotencyKey);

		assertThat(ordersInTheLedger()).isEqualTo(before);
		return response;
	}

	protected ResultActions rejected(long userId, String body) throws Exception {
		long before = ordersInTheLedger();

		ResultActions response = submit(userId, body).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("REJECTED"));

		assertThat(ordersInTheLedger()).isEqualTo(before + 1);
		return response;
	}

	protected Connection lockedUser(long userId) throws SQLException {
		Connection connection = dataSource.getConnection();
		connection.setAutoCommit(false);
		connection.createStatement().execute("SELECT id FROM users WHERE id = " + userId + " FOR UPDATE");
		return connection;
	}

	protected long ordersInTheLedger() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class);
	}

	protected long claimedKeys() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM order_idempotency", Long.class);
	}

	protected void fund(long userId, long pesos) {
		jdbcTemplate.update("""
				INSERT INTO orders (instrumentId, userId, size, price, type, side, status, datetime)
				VALUES (?, ?, ?, 1, 'MARKET', 'CASH_IN', 'FILLED', now())
				""", ARS, userId, pesos);
	}

	protected List<String> outcomesOf(long firstUser, String first, long secondUser, String second) {
		return Simultaneously.theseTwo(() -> createdBodyOf(submit(firstUser, first)),
				() -> createdBodyOf(submit(secondUser, second)));
	}

	protected static List<String> statusesIn(List<String> outcomes) {
		return outcomes.stream().map(outcome -> reportedAs(outcome, "status")).toList();
	}

	protected static String rejectionIn(List<String> outcomes) {
		return outcomes.stream()
			.filter(outcome -> "REJECTED".equals(reportedAs(outcome, "status")))
			.map(outcome -> reportedAs(outcome, "rejectionReason"))
			.findFirst()
			.orElseThrow();
	}

	protected static String createdBodyOf(ResultActions response) throws Exception {
		return response.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
	}

	protected static String reportedAs(String body, String field) {
		Object value = JsonPath.read(body, "$." + field);
		return String.valueOf(value);
	}

	protected static String marketOrderOf(long instrumentId, String side, String quantity) {
		return "{\"instrumentId\": " + instrumentId + ", \"type\": \"MARKET\", \"side\": \"" + side + "\", " + quantity
				+ "}";
	}

	protected static String limitOrderOf(long instrumentId, String side, String quantity, String price) {
		return "{\"instrumentId\": " + instrumentId + ", \"type\": \"LIMIT\", \"side\": \"" + side + "\", " + quantity
				+ ", \"price\": " + price + "}";
	}

	protected static String sizeOf(long size) {
		return "\"size\": " + size;
	}

	protected static String amountOf(String amount) {
		return "\"amount\": " + amount;
	}

}
