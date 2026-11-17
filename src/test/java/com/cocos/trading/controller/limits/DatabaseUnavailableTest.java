package com.cocos.trading.controller.limits;

import static com.cocos.trading.controller.ApiPaths.INSTRUMENT_SEARCH;
import static com.cocos.trading.controller.ApiPaths.PORTFOLIO;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.cocos.trading.TestcontainersConfiguration;
import com.cocos.trading.controller.ContractValidationConfiguration;

class DatabaseUnavailableTest {

	private static final String UNAVAILABLE = "The service is temporarily unavailable, try again later";

	private static ResultActions expectUnavailable(ResultActions response) throws Exception {
		return response.andExpect(status().isServiceUnavailable())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.detail").value(UNAVAILABLE));
	}

	@Nested
	@SpringBootTest(properties = "spring.transaction.default-timeout=1s")
	@AutoConfigureMockMvc
	@Import({ TestcontainersConfiguration.class, ContractValidationConfiguration.class })
	class WhenAQueryRunsPastTheTransactionTimeout {

		@Autowired
		private MockMvc mvc;

		@Autowired
		private DataSource dataSource;

		@Test
		void the_search_answers_503() throws Exception {
			try (Connection blocker = lockedTable("instruments")) {
				expectUnavailable(mvc.perform(get(INSTRUMENT_SEARCH).param("query", "pamp")));
				blocker.rollback();
			}
		}

		@Test
		void the_portfolio_answers_503() throws Exception {
			try (Connection blocker = lockedTable("orders")) {
				expectUnavailable(mvc.perform(get(PORTFOLIO, 1)));
				blocker.rollback();
			}
		}

		private Connection lockedTable(String table) throws SQLException {
			Connection connection = dataSource.getConnection();
			connection.setAutoCommit(false);
			connection.createStatement().execute("LOCK TABLE " + table + " IN ACCESS EXCLUSIVE MODE");
			return connection;
		}

	}

	@Nested
	@SpringBootTest(properties = { "spring.datasource.hikari.maximum-pool-size=1",
			"spring.datasource.hikari.connection-timeout=250" })
	@AutoConfigureMockMvc
	@Import({ TestcontainersConfiguration.class, ContractValidationConfiguration.class })
	class WhenNoConnectionComesFreeInThePool {

		@Autowired
		private MockMvc mvc;

		@Autowired
		private DataSource dataSource;

		@Test
		void the_request_answers_503() throws Exception {
			try (Connection onlyConnection = dataSource.getConnection()) {
				expectUnavailable(mvc.perform(get(PORTFOLIO, 1)));
			}
		}

	}

}
