package com.cocos.trading.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SchemaConstraintsTest extends PersistenceTestBase {

	private static final List<String> ORDER_COLUMNS = List.of("instrumentid", "userid", "size", "price", "type", "side",
			"status", "datetime");

	private static final List<String> VALID_ORDER = List.of("1", "1", "10", "100.00", "'MARKET'", "'BUY'", "'FILLED'",
			"'2023-07-14 10:00:00'");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void a_valid_order_is_accepted() {
		assertThat(jdbcTemplate.update(orderOf(VALID_ORDER))).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "instrumentid", "userid", "size", "price", "type", "side", "status", "datetime" })
	void an_order_missing_any_column_is_rejected(String column) {
		List<String> values = new ArrayList<>(VALID_ORDER);
		values.set(ORDER_COLUMNS.indexOf(column), "NULL");

		assertRejected(orderOf(values));
	}

	@ParameterizedTest
	@ValueSource(strings = { "NULL, '2023-07-15'", "1, NULL" })
	void market_data_without_an_instrument_or_a_date_is_rejected(String instrumentAndDate) {
		assertRejected("INSERT INTO marketdata (instrumentid, date) VALUES (" + instrumentAndDate + ")");
	}

	private static String orderOf(List<String> values) {
		return "INSERT INTO orders (" + String.join(", ", ORDER_COLUMNS) + ") VALUES (" + String.join(", ", values)
				+ ")";
	}

	private void assertRejected(String sql) {
		assertThatThrownBy(() -> jdbcTemplate.execute(sql)).isInstanceOf(DataIntegrityViolationException.class);
	}

}
