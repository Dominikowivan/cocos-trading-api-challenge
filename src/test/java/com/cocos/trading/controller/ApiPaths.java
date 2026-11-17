package com.cocos.trading.controller;

public final class ApiPaths {

	public static final String PORTFOLIO = "/api/v1/users/{userId}/portfolio";

	public static final String INSTRUMENT_SEARCH = "/api/v1/instruments/search";

	public static final String ORDERS = "/api/v1/users/{userId}/orders";

	public static final String CASH_TRANSFERS = "/api/v1/users/{userId}/cash-transfers";

	public static final String CANCEL = "/api/v1/users/{userId}/orders/{orderId}/cancel";

	private ApiPaths() {
	}

}
