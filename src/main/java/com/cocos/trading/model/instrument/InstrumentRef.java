package com.cocos.trading.model.instrument;

public record InstrumentRef(long instrumentId, String ticker, String name, String type) {

	private static final String CASH = "MONEDA";

	public boolean isCash() {
		return CASH.equals(type);
	}

}
