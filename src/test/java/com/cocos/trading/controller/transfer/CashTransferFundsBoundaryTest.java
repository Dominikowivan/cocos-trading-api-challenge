package com.cocos.trading.controller.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.cocos.trading.controller.CashTransferWebTestBase;

class CashTransferFundsBoundaryTest extends CashTransferWebTestBase {

	@Test
	void one_peso_above_the_available_cash_is_rejected_and_exactly_the_available_cash_is_filled() throws Exception {
		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo("627500.00");

		assertThat(reportedAs(withdrawn(SEEDED_USER, 627501), "status")).isEqualTo("REJECTED");
		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo("627500.00");

		assertThat(reportedAs(withdrawn(SEEDED_USER, 627500), "status")).isEqualTo("FILLED");
		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo("0.00");
	}

	@Test
	void reserved_pesos_cannot_be_withdrawn_although_they_are_settled() throws Exception {
		assertThat(settledCashOf(SEEDED_USER)).isEqualByComparingTo("753000.00");
		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo("627500.00");

		String rejected = withdrawn(SEEDED_USER, 700000);

		assertThat(reportedAs(rejected, "status")).isEqualTo("REJECTED");
		assertThat(reportedAs(rejected, "rejectionReason")).isEqualTo("INSUFFICIENT_FUNDS");
		assertThat(availableCashOf(SEEDED_USER)).isEqualByComparingTo("627500.00");
		assertThat(settledCashOf(SEEDED_USER)).isEqualByComparingTo("753000.00");
	}

}
