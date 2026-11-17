package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import org.junit.jupiter.api.Test;

class CashTransferUserIsolationTest extends CashTransferWebTestBase {

	@Test
	void a_transfer_never_waits_for_another_users_lock() throws Exception {
		try (Connection heldByAnotherTransaction = lockedUser(NEVER_TRADED_USER)) {
			String withdrawal = withdrawn(SEEDED_USER, 1000);

			assertThat(reportedAs(withdrawal, "status")).isEqualTo("FILLED");
			heldByAnotherTransaction.rollback();
		}
	}

}
