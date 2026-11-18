package com.cocos.trading.persistence.idempotency;

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

import com.cocos.trading.persistence.PersistenceTestBase;

@Transactional
class OrderIdempotencySchemaTest extends PersistenceTestBase {

	private static final String SEEDED_USER = "1";

	private static final String SEEDED_ORDER = "1";

	private static final String A_FINGERPRINT = "'" + "a".repeat(64) + "'";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void v5_applied_on_top_of_the_given_schema() {
		assertThat(migrations()).contains("5").contains("1", "2", "3", "4");
	}

	@Test
	void v6_applied_on_top_of_v5() {
		assertThat(migrations()).contains("6");
	}

	@Test
	void two_claims_cannot_name_the_same_order() {
		jdbcTemplate.update(claimOf(SEEDED_USER, "'first-key'", SEEDED_ORDER));
		String aSecondKeyNamingTheSameOrder = claimOf(SEEDED_USER, "'second-key'", SEEDED_ORDER);

		assertThatThrownBy(() -> jdbcTemplate.execute(aSecondKeyNamingTheSameOrder))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void two_claims_with_no_order_yet_are_both_accepted() {
		jdbcTemplate.update(claimOf(SEEDED_USER, "'claimed-a'", "NULL"));

		assertThat(jdbcTemplate.update(claimOf(SEEDED_USER, "'claimed-b'", "NULL"))).isEqualTo(1);
	}

	@Test
	void a_key_claimed_by_an_existing_user_is_accepted() {
		assertThat(jdbcTemplate.update(claimOf(SEEDED_USER, "'a-key'", "NULL"))).isEqualTo(1);
	}

	@Test
	void the_same_key_cannot_be_claimed_twice_by_one_user() {
		String theSameKeyAgain = claimOf(SEEDED_USER, "'twice'", "NULL");
		jdbcTemplate.update(theSameKeyAgain);

		assertThatThrownBy(() -> jdbcTemplate.execute(theSameKeyAgain))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void the_same_key_claimed_by_another_user_is_another_claim() {
		jdbcTemplate.update(claimOf(SEEDED_USER, "'shared'", "NULL"));

		assertThat(jdbcTemplate.update(claimOf("2", "'shared'", "NULL"))).isEqualTo(1);
	}

	@Test
	void a_claim_names_the_order_it_produced() {
		assertThat(jdbcTemplate.update(claimOf(SEEDED_USER, "'with-an-order'", SEEDED_ORDER))).isEqualTo(1);
	}

	@Test
	void a_claim_for_a_user_that_does_not_exist_is_refused() {
		String forAUserThatDoesNotExist = claimOf("999999", "'orphan'", "NULL");

		assertThatThrownBy(() -> jdbcTemplate.execute(forAUserThatDoesNotExist))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void a_claim_naming_an_order_that_does_not_exist_is_refused() {
		String namingAnOrderThatDoesNotExist = claimOf(SEEDED_USER, "'phantom'", "999999");

		assertThatThrownBy(() -> jdbcTemplate.execute(namingAnOrderThatDoesNotExist))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "userid", "idempotency_key", "request_fingerprint", "created_at" })
	void the_columns_a_claim_cannot_exist_without_are_required(String column) {
		assertThat(nullableColumns()).doesNotContain(column);
	}

	@Test
	void the_order_is_nullable_because_it_is_attached_after_the_claim() {
		assertThat(nullableColumns()).contains("order_id");
	}

	@Test
	void the_key_is_unique_per_user_and_the_order_is_unique_on_its_own() {
		assertThat(uniqueConstraintColumns()).containsExactlyInAnyOrder("userid", "idempotency_key", "order_id");
	}

	@Test
	void the_user_and_the_order_are_foreign_keys() {
		assertThat(foreignKeyTargets()).containsExactlyInAnyOrder("users", "orders");
	}

	private List<String> migrations() {
		return jdbcTemplate.queryForList("SELECT version FROM flyway_schema_history WHERE success", String.class);
	}

	private List<String> nullableColumns() {
		return jdbcTemplate.queryForList("""
				SELECT column_name FROM information_schema.columns
				WHERE table_name = 'order_idempotency' AND is_nullable = 'YES'
				""", String.class);
	}

	private List<String> uniqueConstraintColumns() {
		return jdbcTemplate.queryForList("""
				SELECT a.attname FROM pg_constraint c
				JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
				WHERE c.conrelid = 'order_idempotency'::regclass AND c.contype = 'u'
				""", String.class);
	}

	private List<String> foreignKeyTargets() {
		return jdbcTemplate.queryForList("""
				SELECT confrelid::regclass::text FROM pg_constraint
				WHERE conrelid = 'order_idempotency'::regclass AND contype = 'f'
				""", String.class);
	}

	private static String claimOf(String userId, String key, String orderId) {
		return "INSERT INTO order_idempotency (userid, idempotency_key, request_fingerprint, order_id, created_at)"
				+ " VALUES (" + userId + ", " + key + ", " + A_FINGERPRINT + ", " + orderId + ", now())";
	}

}
