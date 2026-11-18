package com.cocos.trading.model.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DailyClosesTest {

	private static final InstrumentRef PAMP = new InstrumentRef(47, "PAMP", "Pampa Holding S.A.", "ACCIONES");

	private static final InstrumentRef PGR = new InstrumentRef(3, "PGR", "Phoenix Global Resources", "ACCIONES");

	private static final DailyCloses ONLY_PAMP_PRICED = new DailyCloses(
			Map.of(47L, DailyClose.of(new BigDecimal("925.85"), new BigDecimal("921.80"))));

	@Test
	void a_priced_instrument_carries_its_close() {
		assertThat(ONLY_PAMP_PRICED.of(PAMP).close()).contains(new BigDecimal("925.85"));
	}

	@Test
	void an_instrument_with_no_market_data_gets_an_unknown_close_never_zero() {
		DailyClose pgr = ONLY_PAMP_PRICED.of(PGR);

		assertThat(pgr.close()).isEmpty();
		assertThat(pgr.dailyReturnPercent()).isEmpty();
	}

}
