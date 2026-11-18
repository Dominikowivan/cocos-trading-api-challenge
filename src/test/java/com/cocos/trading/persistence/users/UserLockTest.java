package com.cocos.trading.persistence.users;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.cocos.trading.persistence.PersistenceTestBase;
import com.cocos.trading.persistence.UserRepository;

@Transactional
class UserLockTest extends PersistenceTestBase {

	@Autowired
	private UserRepository userRepository;

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 3, 4 })
	void a_seeded_user_is_locked_and_returned(Integer userId) {
		assertThat(userRepository.lockById(userId)).isPresent();
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, 99999 })
	void a_user_that_does_not_exist_locks_nothing(Integer userId) {
		assertThat(userRepository.lockById(userId)).isEmpty();
	}

	@Test
	void locking_the_same_user_twice_in_one_transaction_is_not_a_deadlock() {
		assertThat(userRepository.lockById(1)).isPresent();
		assertThat(userRepository.lockById(1)).isPresent();
	}

}
