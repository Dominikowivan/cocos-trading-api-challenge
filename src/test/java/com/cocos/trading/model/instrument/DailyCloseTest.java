package com.cocos.trading.model.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class DailyCloseTest {

	@Test
	void the_days_move_is_the_change_over_the_previous_close_in_percent_units() {
		assertThat(dailyClose("925.85", "921.80").dailyReturnPercent()).contains(new BigDecimal("0.44"));
	}

	@Test
	void a_fall_is_negative() {
		assertThat(dailyClose("33.40", "34.60").dailyReturnPercent()).contains(new BigDecimal("-3.47"));
	}

	@Test
	void the_figure_is_rounded_half_up_at_scale_two() {
		assertThat(dailyClose("77.00", "75.00").dailyReturnPercent()).contains(new BigDecimal("2.67"));
	}

	@Test
	void a_flat_day_is_a_known_zero_not_an_absent_figure() {
		assertThat(dailyClose("138.00", "138.00").dailyReturnPercent()).contains(new BigDecimal("0.00"));
	}

	@Test
	void an_absent_close_leaves_both_figures_absent() {
		DailyClose dailyClose = DailyClose.of(null, new BigDecimal("921.80"));

		assertThat(dailyClose.close()).isEmpty();
		assertThat(dailyClose.dailyReturnPercent()).isEmpty();
	}

	@Test
	void an_absent_previous_close_leaves_the_return_absent_but_still_reports_the_price() {
		DailyClose dailyClose = DailyClose.of(new BigDecimal("925.85"), null);

		assertThat(dailyClose.close()).contains(new BigDecimal("925.85"));
		assertThat(dailyClose.dailyReturnPercent()).isEmpty();
	}

	@Test
	void a_previous_close_of_zero_leaves_the_return_absent_rather_than_dividing_by_it() {
		assertThat(dailyClose("925.85", "0").dailyReturnPercent()).isEmpty();
	}

	@Test
	void a_previous_close_of_zero_at_scale_two_is_guarded_too() {
		assertThat(dailyClose("925.85", "0.00").dailyReturnPercent()).isEmpty();
	}

	@Test
	void an_instrument_with_no_market_data_at_all_reports_nothing() {
		DailyClose unknown = DailyClose.unknown();

		assertThat(unknown.close()).isEmpty();
		assertThat(unknown.dailyReturnPercent()).isEmpty();
	}

	private static DailyClose dailyClose(String close, String previousClose) {
		return DailyClose.of(new BigDecimal(close), new BigDecimal(previousClose));
	}

}
