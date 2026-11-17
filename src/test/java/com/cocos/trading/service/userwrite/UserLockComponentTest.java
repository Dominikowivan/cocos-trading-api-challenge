package com.cocos.trading.service.userwrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.cocos.trading.TestcontainersConfiguration;
import com.cocos.trading.model.user.UnknownUserException;
import com.cocos.trading.model.user.UserId;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserLockComponentTest {

	@Autowired
	private UserLock userLock;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void inside_a_transaction_the_work_runs_with_the_users_column() {
		Integer column = new TransactionTemplate(transactionManager)
			.execute(status -> userLock.underTheLockOf(new UserId(1), userIdColumn -> userIdColumn));

		assertThat(column).isEqualTo(1);
	}

	@Test
	void an_unknown_user_has_no_row_to_lock() {
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);

		assertThatThrownBy(() -> transaction
			.execute(status -> userLock.underTheLockOf(new UserId(999), userIdColumn -> userIdColumn)))
			.isInstanceOf(UnknownUserException.class);
	}

	@Test
	void outside_a_transaction_it_is_refused_and_does_no_work() {
		AtomicBoolean worked = new AtomicBoolean();

		assertThatIllegalStateException().isThrownBy(() -> userLock.underTheLockOf(new UserId(1), userIdColumn -> {
			worked.set(true);
			return userIdColumn;
		})).withMessageContaining("transaction");

		assertThat(worked).isFalse();
	}

}
