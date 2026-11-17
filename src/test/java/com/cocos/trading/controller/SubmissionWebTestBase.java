package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

abstract class SubmissionWebTestBase extends WebTestBase {

	protected static final String ORDERS = "/api/v1/users/{userId}/orders";

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

	@AfterEach
	void restoreTheSeed() {
		jdbcTemplate.update("DELETE FROM order_status_history WHERE order_id > ?", SEEDED_ORDERS);
		jdbcTemplate.update("DELETE FROM orders WHERE id > ?", SEEDED_ORDERS);
	}

	protected ResultActions submit(long userId, String body) throws Exception {
		return mvc.perform(post(ORDERS, userId).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	protected ResultActions refused(long userId, String body) throws Exception {
		long before = ordersInTheLedger();

		ResultActions response = submit(userId, body);

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

	protected long ordersInTheLedger() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class);
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
