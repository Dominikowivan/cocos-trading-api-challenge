package com.cocos.trading.model.user;

public record UserId(long value) {

	@Override
	public String toString() {
		return Long.toString(value);
	}

}
