package com.cocos.trading.controller;

import static com.cocos.trading.controller.ApiPaths.CASH_TRANSFERS;
import static com.cocos.trading.controller.ApiPaths.PORTFOLIO;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

public abstract class CashTransferWebTestBase extends SubmissionWebTestBase {

	protected static final long USER_THREE = 3;

	protected static final long USER_FOUR = 4;

	protected static final long UNKNOWN_USER = 999;

	protected ResultActions transfer(long userId, String body) throws Exception {
		return mvc.perform(post(CASH_TRANSFERS, userId).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	protected ResultActions transfer(long userId, String body, String idempotencyKey) throws Exception {
		return mvc.perform(post(CASH_TRANSFERS, userId).contentType(MediaType.APPLICATION_JSON)
			.header(IDEMPOTENCY_KEY, idempotencyKey)
			.content(body));
	}

	protected ResultActions refusedTransfer(long userId, String body) throws Exception {
		return leavingTheLedgerUntouched(() -> transfer(userId, body));
	}

	protected ResultActions refusedTransfer(long userId, String body, String idempotencyKey) throws Exception {
		return leavingTheLedgerUntouched(() -> transfer(userId, body, idempotencyKey));
	}

	protected String deposited(long userId, long pesos) throws Exception {
		return createdBodyOf(transfer(userId, depositOf(pesos)));
	}

	protected String withdrawn(long userId, long pesos) throws Exception {
		return createdBodyOf(transfer(userId, withdrawalOf(pesos)));
	}

	protected BigDecimal availableCashOf(long userId) throws Exception {
		return cashFigure(userId, "availableCash");
	}

	protected BigDecimal settledCashOf(long userId) throws Exception {
		return cashFigure(userId, "settledCash");
	}

	protected long ordersOf(long userId) {
		return jdbc().queryForObject("SELECT count(*) FROM orders WHERE userid = ?", Long.class, userId);
	}

	protected static String depositOf(long pesos) {
		return transferOf("CASH_IN", Long.toString(pesos));
	}

	protected static String withdrawalOf(long pesos) {
		return transferOf("CASH_OUT", Long.toString(pesos));
	}

	protected static String transferOf(String side, String amount) {
		return "{\"side\": \"" + side + "\", \"amount\": " + amount + "}";
	}

	protected static BigDecimal amountIn(String body, String field) {
		return new BigDecimal(reportedAs(body, field));
	}

	private BigDecimal cashFigure(long userId, String field) throws Exception {
		return amountIn(bodyOf(mvc.perform(get(PORTFOLIO, userId))), field);
	}

}
