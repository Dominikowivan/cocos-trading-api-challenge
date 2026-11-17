package com.cocos.trading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

class SubmissionPortfolioAgreementTest extends SubmissionWebTestBase {

	private static final BigDecimal PAMP_LATEST_CLOSE = new BigDecimal("925.85");

	@Test
	void the_cash_a_submission_validated_against_is_the_cash_the_portfolio_reports() throws Exception {
		BigDecimal availableCash = availableCashOf(SEEDED_USER);

		rejected(SEEDED_USER, marketOrderOf(PAMP, "BUY", sizeOf(sharesJustPastTheCashAt(availableCash))))
			.andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_FUNDS"));
		submit(SEEDED_USER, marketOrderOf(PAMP, "BUY", sizeOf(sharesTheCashBuysAt(availableCash))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("FILLED"));
	}

	@Test
	void an_order_is_visible_in_the_next_portfolio_read_to_the_cent() throws Exception {
		BigDecimal before = availableCashOf(SEEDED_USER);

		String order = createdBodyOf(submit(SEEDED_USER, marketOrderOf(PAMP, "BUY", sizeOf(100))));

		BigDecimal spent = new BigDecimal(reportedAs(order, "price")).multiply(BigDecimal.valueOf(100));
		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo(before.subtract(spent));
	}

	@Test
	void a_limit_order_reserves_in_the_portfolio_what_it_committed() throws Exception {
		BigDecimal before = availableCashOf(SEEDED_USER);
		BigDecimal settledBefore = settledCashOf(SEEDED_USER);

		submit(SEEDED_USER, limitOrderOf(PAMP, "BUY", sizeOf(100), "900.00")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("NEW"));

		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo(before.subtract(new BigDecimal("90000.00")));
		assertThat(settledCashOf(SEEDED_USER)).isEqualByComparingTo(settledBefore);
	}

	@Test
	void a_recorded_rejection_moves_no_figure_in_the_portfolio() throws Exception {
		BigDecimal before = availableCashOf(SEEDED_USER);
		BigDecimal settledBefore = settledCashOf(SEEDED_USER);

		rejected(SEEDED_USER, marketOrderOf(PAMP, "BUY", sizeOf(1000)));

		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo(before);
		assertThat(settledCashOf(SEEDED_USER)).isEqualByComparingTo(settledBefore);
	}

	@Test
	void a_sale_is_visible_as_both_fewer_shares_and_more_cash() throws Exception {
		BigDecimal before = availableCashOf(SEEDED_USER);

		String order = createdBodyOf(submit(SEEDED_USER, marketOrderOf(PAMP, "SELL", sizeOf(10))));

		BigDecimal received = new BigDecimal(reportedAs(order, "price")).multiply(BigDecimal.TEN);
		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo(before.add(received));
		mvc.perform(get(PORTFOLIO, SEEDED_USER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.positions[?(@.ticker == 'PAMP')].quantity").value(contains(30)));
	}

	private long sharesTheCashBuysAt(BigDecimal availableCash) {
		return availableCash.divideToIntegralValue(PAMP_LATEST_CLOSE).longValueExact();
	}

	private long sharesJustPastTheCashAt(BigDecimal availableCash) {
		return sharesTheCashBuysAt(availableCash) + 1;
	}

	private BigDecimal availableCashOf(long userId) throws Exception {
		return figureOf(userId, "availableCash");
	}

	private BigDecimal settledCashOf(long userId) throws Exception {
		return figureOf(userId, "settledCash");
	}

	private BigDecimal figureOf(long userId, String field) throws Exception {
		String portfolio = mvc.perform(get(PORTFOLIO, userId))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return new BigDecimal(String.valueOf(JsonPath.<Object>read(portfolio, "$." + field)));
	}

}
