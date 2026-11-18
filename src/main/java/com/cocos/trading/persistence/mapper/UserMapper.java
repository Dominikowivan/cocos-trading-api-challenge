package com.cocos.trading.persistence.mapper;

import java.util.Optional;

import com.cocos.trading.model.user.UserId;

public final class UserMapper {

	private UserMapper() {
	}

	public static Optional<Integer> toIdColumn(UserId userId) {
		boolean doesNotFitTheIdColumn = userId.value() < Integer.MIN_VALUE || userId.value() > Integer.MAX_VALUE;

		if (doesNotFitTheIdColumn) {
			return Optional.empty();
		}
		return Optional.of((int) userId.value());
	}

}
