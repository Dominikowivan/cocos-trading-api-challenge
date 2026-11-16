package com.cocos.trading.model.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class CostBasisWalkTest {

	@Test
	void a_flat_position_takes_the_orders_quantity_and_price() {
		CostBasisWalk walk = new CostBasisWalk();

		walk.accept(quantity(20), price("1540"));

		assertThat(walk.quantity()).isEqualByComparingTo("20");
		assertThat(walk.averageCost()).isEqualByComparingTo("1540");
	}

	@Test
	void an_extending_order_averages_the_two_legs_by_weight() {
		CostBasisWalk walk = new CostBasisWalk();

		walk.accept(quantity(10), price("100"));
		walk.accept(quantity(30), price("200"));

		assertThat(walk.quantity()).isEqualByComparingTo("40");
		assertThat(walk.averageCost()).isEqualByComparingTo("175");
	}

	@Test
	void an_extending_order_does_not_keep_the_oldest_lots_price() {
		CostBasisWalk walk = new CostBasisWalk();

		walk.accept(quantity(10), price("100"));
		walk.accept(quantity(10), price("200"));

		assertThat(walk.averageCost()).isEqualByComparingTo("150");
	}

	@Test
	void a_partial_opposite_order_leaves_the_average_untouched() {
		CostBasisWalk walk = new CostBasisWalk();

		walk.accept(quantity(50), price("930"));
		walk.accept(quantity(-10), price("940"));

		assertThat(walk.quantity()).isEqualByComparingTo("40");
		assertThat(walk.averageCost()).isEqualByComparingTo("930");
	}

	@Test
	void an_exact_close_resets_quantity_and_average_to_zero() {
		CostBasisWalk walk = new CostBasisWalk();

		walk.accept(quantity(20), price("1540"));
		walk.accept(quantity(-20), price("1530"));

		assertThat(walk.quantity()).isEqualByComparingTo("0");
		assertThat(walk.averageCost()).isEqualByComparingTo("0");
	}

	@Test
	void reopening_after_a_reset_carries_nothing_forward() {
		CostBasisWalk walk = new CostBasisWalk();

		walk.accept(quantity(20), price("1540"));
		walk.accept(quantity(-20), price("1530"));
		walk.accept(quantity(5), price("1000"));

		assertThat(walk.quantity()).isEqualByComparingTo("5");
		assertThat(walk.averageCost()).isEqualByComparingTo("1000");
	}

	private static BigDecimal quantity(long size) {
		return BigDecimal.valueOf(size);
	}

	private static BigDecimal price(String amount) {
		return new BigDecimal(amount);
	}

}
