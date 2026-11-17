package com.cocos.trading.controller.submission;

import static com.cocos.trading.controller.ApiPaths.PORTFOLIO;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.cocos.trading.controller.SubmissionWebTestBase;

class SubmissionAmountRefusalTest extends SubmissionWebTestBase {

	private static final String ONE_OF_THE_TWO = "An order states either a size in whole shares or a total amount in pesos, never both and never neither";

	@Test
	void an_amount_beyond_the_available_cash_is_a_recorded_rejection() throws Exception {
		rejected(SEEDED_USER, marketOrderOf(PAMP, "BUY", amountOf("700000.00")))
			.andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_FUNDS"))
			.andExpect(jsonPath("$.size").value(0))
			.andExpect(jsonPath("$.price").value(925.85));
	}

	@Test
	void money_that_does_not_reach_one_share_is_a_recorded_rejection_of_its_own() throws Exception {
		rejected(SEEDED_USER, marketOrderOf(PAMP, "BUY", amountOf("500.00")))
			.andExpect(jsonPath("$.rejectionReason").value("SIZE_BELOW_ONE_SHARE"))
			.andExpect(jsonPath("$.size").value(0));
	}

	@Test
	void an_amount_worth_more_than_the_position_is_a_recorded_rejection() throws Exception {
		rejected(SEEDED_USER, marketOrderOf(PAMP, "SELL", amountOf("40000.00")))
			.andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_HOLDINGS"))
			.andExpect(jsonPath("$.size").value(0));
	}

	@Test
	void a_sale_for_less_than_one_share_is_a_recorded_rejection() throws Exception {
		rejected(SEEDED_USER, marketOrderOf(PAMP, "SELL", amountOf("900.00")))
			.andExpect(jsonPath("$.rejectionReason").value("SIZE_BELOW_ONE_SHARE"));
	}

	@Test
	void a_rejection_on_the_amount_path_changes_no_figure_in_the_portfolio() throws Exception {
		rejected(SEEDED_USER, marketOrderOf(PAMP, "BUY", amountOf("700000.00")));

		mvc.perform(get(PORTFOLIO, SEEDED_USER)).andExpect(jsonPath("$.availableCash").value(627500.00));
	}

	@Test
	void a_request_that_states_both_a_size_and_an_amount_is_refused() throws Exception {
		refused(SEEDED_USER, marketOrderOf(PAMP, "BUY", sizeOf(10) + ", " + amountOf("1000.00")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(ONE_OF_THE_TWO));
	}

	@Test
	void a_request_that_states_neither_is_refused_with_the_same_rule() throws Exception {
		refused(SEEDED_USER, "{\"instrumentId\": 47, \"type\": \"MARKET\", \"side\": \"BUY\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(ONE_OF_THE_TWO));
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "0.00", "-0.01", "-1000.00", "100000000.00" })
	void an_amount_outside_the_ledgers_bounds_is_refused_before_any_money_is_looked_at(String amount) throws Exception {
		refused(SEEDED_USER, marketOrderOf(PAMP, "BUY", amountOf(amount))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("A total amount is between 0.01 and 99999999.99 pesos, and "
					+ atTheLedgersScale(amount) + " is not"));
	}

	private static String atTheLedgersScale(String amount) {
		return new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

}
