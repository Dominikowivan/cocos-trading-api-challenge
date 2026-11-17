package com.cocos.trading.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class OrderStatusHistorySchemaTest extends PersistenceTestBase {

	private static final String SEEDED_ORDER = "1";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void v4_applied_on_top_of_the_given_schema() {
		assertThat(migrations()).contains("4").contains("1", "2", "3");
	}

	@Test
	void a_transition_of_an_existing_order_is_accepted() {
		assertThat(jdbcTemplate.update(transitionOf(SEEDED_ORDER, "'FILLED'", "NULL", "'2026-11-17 10:00:00'")))
			.isEqualTo(1);
	}

	@Test
	void a_rejection_carries_its_reason_and_an_acceptance_does_not_have_to() {
		assertThat(jdbcTemplate
			.update(transitionOf(SEEDED_ORDER, "'REJECTED'", "'INSUFFICIENT_FUNDS'", "'2026-11-17 10:00:00'")))
			.isEqualTo(1);
	}

	@Test
	void a_transition_of_an_order_that_does_not_exist_is_refused() {
		String ofAnOrderThatDoesNotExist = transitionOf("999999", "'FILLED'", "NULL", "'2026-11-17 10:00:00'");

		assertThatThrownBy(() -> jdbcTemplate.execute(ofAnOrderThatDoesNotExist))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "order_id", "status", "changed_at" })
	void the_columns_a_transition_cannot_exist_without_are_required(String column) {
		assertThat(nullableColumns()).doesNotContain(column);
	}

	@Test
	void a_reason_is_optional_because_an_acceptance_has_none() {
		assertThat(nullableColumns()).contains("reason");
	}

	@Test
	void the_history_of_one_order_is_indexed() {
		assertThat(indexedColumns()).contains("order_id");
	}

	@Test
	void the_order_a_transition_belongs_to_is_a_foreign_key() {
		assertThat(foreignKeyTargets()).contains("orders");
	}

	private List<String> migrations() {
		return jdbcTemplate.queryForList("SELECT version FROM flyway_schema_history WHERE success", String.class);
	}

	private List<String> nullableColumns() {
		return jdbcTemplate.queryForList("""
				SELECT column_name FROM information_schema.columns
				WHERE table_name = 'order_status_history' AND is_nullable = 'YES'
				""", String.class);
	}

	private List<String> indexedColumns() {
		return jdbcTemplate.queryForList("""
				SELECT a.attname FROM pg_index i
				JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
				WHERE i.indrelid = 'order_status_history'::regclass
				""", String.class);
	}

	private List<String> foreignKeyTargets() {
		return jdbcTemplate.queryForList("""
				SELECT confrelid::regclass::text FROM pg_constraint
				WHERE conrelid = 'order_status_history'::regclass AND contype = 'f'
				""", String.class);
	}

	private static String transitionOf(String orderId, String status, String reason, String changedAt) {
		return "INSERT INTO order_status_history (order_id, status, reason, changed_at) VALUES (" + orderId + ", "
				+ status + ", " + reason + ", " + changedAt + ")";
	}

}
