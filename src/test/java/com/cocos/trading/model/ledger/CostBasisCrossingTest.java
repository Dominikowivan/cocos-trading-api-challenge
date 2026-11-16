package com.cocos.trading.model.ledger;

import static com.cocos.trading.model.ledger.Ledgers.BMA;
import static com.cocos.trading.model.ledger.Ledgers.filledBuy;
import static com.cocos.trading.model.ledger.Ledgers.filledSell;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class CostBasisCrossingTest {

	@Test
	void a_sale_through_zero_rebases_the_position_on_the_crossing_price() {
		CostBasisWalk walk = new CostBasisWalk();

		walk.accept(BigDecimal.valueOf(20), new BigDecimal("100"));
		walk.accept(BigDecimal.valueOf(-50), new BigDecimal("120"));

		assertThat(walk.quantity()).isEqualByComparingTo("-30");
		assertThat(walk.averageCost()).isEqualByComparingTo("120");
	}

	@Test
	void the_seeded_short_reopened_long_is_based_on_the_reopening_price() {
		Holding bma = holdingOf(new AccountLedger(Ledgers.orders(filledBuy(BMA, 20, "1540", "2023-07-13T12:51:20"),
				filledSell(BMA, 30, "1530", "2023-07-13T15:13:20"),
				filledBuy(BMA, 30, "1502.80", "2023-07-14T16:00:00")))
			.snapshot());

		assertThat(bma.quantity()).isEqualByComparingTo("20");
		assertThat(bma.averageCost()).isEqualByComparingTo("1502.80");
		assertThat(bma.averageCost()).isNotEqualByComparingTo("1540");
		assertThat(bma.totalCost()).isEqualByComparingTo("30056.00");
	}

	@Test
	void the_price_that_closed_the_short_is_not_carried_into_the_new_basis() {
		Holding bma = holdingOf(new AccountLedger(Ledgers.orders(filledBuy(BMA, 20, "1540", "2023-07-13T12:51:20"),
				filledSell(BMA, 30, "1530", "2023-07-13T15:13:20"),
				filledBuy(BMA, 30, "1502.80", "2023-07-14T16:00:00")))
			.snapshot());

		assertThat(bma.averageCost()).isNotEqualByComparingTo("1530");
	}

	private static Holding holdingOf(AccountSnapshot snapshot) {
		return snapshot.holdings().getFirst();
	}

}
