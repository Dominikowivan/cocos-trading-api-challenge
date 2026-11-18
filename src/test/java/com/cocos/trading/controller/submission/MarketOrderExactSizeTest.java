package com.cocos.trading.controller.submission;

import static com.cocos.trading.controller.ApiPaths.PORTFOLIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.cocos.trading.controller.SubmissionWebTestBase;

class MarketOrderExactSizeTest extends SubmissionWebTestBase {

	@Test
	void a_purchase_the_user_can_pay_for_is_filled_at_the_last_close() throws Exception {
		submit(SEEDED_USER, marketOrderOf(PAMP, "BUY", sizeOf(100))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("FILLED"))
			.andExpect(jsonPath("$.size").value(100))
			.andExpect(jsonPath("$.price").value(925.85))
			.andExpect(jsonPath("$.rejectionReason").value(nullValue()))
			.andExpect(jsonPath("$.userId").value(SEEDED_USER))
			.andExpect(jsonPath("$.instrumentId").value(PAMP))
			.andExpect(jsonPath("$.ticker").value("PAMP"))
			.andExpect(jsonPath("$.type").value("MARKET"))
			.andExpect(jsonPath("$.side").value("BUY"))
			.andExpect(jsonPath("$.orderId").isNumber())
			.andExpect(jsonPath("$.submittedAt").isString());
	}

	@Test
	void the_purchase_is_visible_in_the_next_portfolio_read() throws Exception {
		submit(SEEDED_USER, marketOrderOf(PAMP, "BUY", sizeOf(100))).andExpect(status().isCreated());

		mvc.perform(get(PORTFOLIO, SEEDED_USER))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.availableCash").value(534915.00));
	}

	@Test
	void six_hundred_and_seventy_seven_shares_are_the_whole_available_cash() throws Exception {
		submit(SEEDED_USER, marketOrderOf(PAMP, "BUY", sizeOf(677))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("FILLED"));
	}

	@Test
	void one_share_past_the_available_cash_is_a_recorded_rejection() throws Exception {
		submit(SEEDED_USER, marketOrderOf(PAMP, "BUY", sizeOf(678))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_FUNDS"))
			.andExpect(jsonPath("$.size").value(678))
			.andExpect(jsonPath("$.price").value(925.85));
	}

	@Test
	void a_rejected_order_is_stored_and_changes_no_figure_in_the_portfolio() throws Exception {
		long before = ordersInTheLedger();

		submit(SEEDED_USER, marketOrderOf(PAMP, "BUY", sizeOf(1000))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("REJECTED"));

		mvc.perform(get(PORTFOLIO, SEEDED_USER)).andExpect(jsonPath("$.availableCash").value(627500.00));
		assertThat(ordersInTheLedger()).isEqualTo(before + 1);
	}

	@Test
	void selling_the_whole_position_is_filled_at_the_last_close() throws Exception {
		submit(SEEDED_USER, marketOrderOf(PAMP, "SELL", sizeOf(40))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("FILLED"))
			.andExpect(jsonPath("$.size").value(40))
			.andExpect(jsonPath("$.price").value(925.85));
	}

	@Test
	void one_share_more_than_the_position_is_a_recorded_rejection() throws Exception {
		submit(SEEDED_USER, marketOrderOf(PAMP, "SELL", sizeOf(41))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_HOLDINGS"));
	}

	@Test
	void a_negative_position_is_reported_but_never_sold_from() throws Exception {
		submit(SEEDED_USER, marketOrderOf(BMA, "SELL", sizeOf(1))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_HOLDINGS"));
	}

	@Test
	void a_user_who_never_traded_cannot_buy() throws Exception {
		submit(NEVER_TRADED_USER, marketOrderOf(PAMP, "BUY", sizeOf(1))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_FUNDS"));
	}

	@Test
	void a_user_who_never_traded_cannot_sell() throws Exception {
		submit(NEVER_TRADED_USER, marketOrderOf(PAMP, "SELL", sizeOf(1))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.rejectionReason").value("INSUFFICIENT_HOLDINGS"));
	}

}
