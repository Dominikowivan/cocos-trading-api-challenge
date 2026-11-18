package com.cocos.trading.controller.cancellation;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import org.junit.jupiter.api.Test;

import com.cocos.trading.controller.CancellationWebTestBase;

class CancellationUserIsolationTest extends CancellationWebTestBase {

	@Test
	void a_cancellation_never_waits_for_another_users_lock() throws Exception {
		try (Connection heldByAnotherTransaction = lockedUser(NEVER_TRADED_USER)) {
			String cancellation = cancelled(SEEDED_USER, PENDING_BUY);

			assertThat(reportedAs(cancellation, "status")).isEqualTo("CANCELLED");
			heldByAnotherTransaction.rollback();
		}
	}

}
