package com.cocos.trading.controller.cancellation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.cocos.trading.controller.CancellationWebTestBase;

class CancelledSellReleasesSharesTest extends CancellationWebTestBase {

	@Test
	void a_cancelled_sell_frees_the_shares_it_reserved() throws Exception {
		String limitSell = createdBodyOf(submit(SEEDED_USER, limitOrderOf(PAMP, "SELL", sizeOf(40), "1000")));
		long limitSellId = Long.parseLong(reportedAs(limitSell, "orderId"));
		assertThat(reportedAs(limitSell, "status")).isEqualTo("NEW");

		String blocked = createdBodyOf(submit(SEEDED_USER, marketOrderOf(PAMP, "SELL", sizeOf(40))));
		assertThat(reportedAs(blocked, "status")).isEqualTo("REJECTED");
		assertThat(reportedAs(blocked, "rejectionReason")).isEqualTo("INSUFFICIENT_HOLDINGS");

		assertThat(reportedAs(cancelled(SEEDED_USER, limitSellId), "status")).isEqualTo("CANCELLED");

		BigDecimal settledBefore = settledCashOf(SEEDED_USER);
		String sold = createdBodyOf(submit(SEEDED_USER, marketOrderOf(PAMP, "SELL", sizeOf(40))));
		assertThat(reportedAs(sold, "status")).isEqualTo("FILLED");
		assertThat(amountIn(sold, "price")).isEqualByComparingTo("925.85");
		assertThat(settledCashOf(SEEDED_USER).subtract(settledBefore)).isEqualByComparingTo("37034.00");

		assertThat(historyOf(limitSellId)).containsExactly(new Transition("NEW", null),
				new Transition("CANCELLED", "USER_REQUESTED"));
	}

}
