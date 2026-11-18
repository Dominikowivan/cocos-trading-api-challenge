package com.cocos.trading.service.userwrite;

import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.cocos.trading.model.user.UnknownUserException;
import com.cocos.trading.model.user.UserId;
import com.cocos.trading.persistence.UserRepository;
import com.cocos.trading.persistence.mapper.UserMapper;

@Component
public class UserWrite {

	private final UserRepository userRepository;

	private final IdempotentWrite idempotentWrite;

	private final UserLock userLock;

	public UserWrite(UserRepository userRepository, IdempotentWrite idempotentWrite, UserLock userLock) {
		this.userRepository = userRepository;
		this.idempotentWrite = idempotentWrite;
		this.userLock = userLock;
	}

	public <T extends WrittenOrder> T write(UserId userId, Optional<KeyedRequest> keyed, IntFunction<T> work,
			IntFunction<T> replay) {
		requireAnExistingUser(userId);
		Supplier<T> lockedWork = () -> userLock.underTheLockOf(userId, work);

		return keyed.map(request -> idempotentWrite.once(userId, request, lockedWork, replay)).orElseGet(lockedWork);
	}

	private void requireAnExistingUser(UserId userId) {
		if (UserMapper.toIdColumn(userId).filter(userRepository::existsById).isEmpty()) {
			throw new UnknownUserException(userId);
		}
	}

}
