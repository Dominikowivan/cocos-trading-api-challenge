package com.cocos.trading.service.userwrite;

import java.util.function.IntFunction;

import org.springframework.stereotype.Component;

import com.cocos.trading.model.user.UnknownUserException;
import com.cocos.trading.model.user.UserId;
import com.cocos.trading.persistence.UserRepository;
import com.cocos.trading.persistence.mapper.UserMapper;

@Component
public class UserLock {

	private final UserRepository userRepository;

	public UserLock(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public <T> T underTheLockOf(UserId userId, IntFunction<T> work) {
		CallersTransaction.require("A user's lock lasts until the use case's transaction ends, so it is taken in one");
		Integer userIdColumn = lockedUser(userId);

		return work.apply(userIdColumn);
	}

	private Integer lockedUser(UserId userId) {
		return UserMapper.toIdColumn(userId)
			.filter(userIdColumn -> userRepository.lockById(userIdColumn).isPresent())
			.orElseThrow(() -> new UnknownUserException(userId));
	}

}
