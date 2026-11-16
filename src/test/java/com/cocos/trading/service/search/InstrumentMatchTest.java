package com.cocos.trading.service.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.instrument.DailyClose;
import com.cocos.trading.model.instrument.DailyCloses;
import com.cocos.trading.model.instrument.InstrumentRef;

class InstrumentMatchTest {

	private static final InstrumentRef PAMP = new InstrumentRef(47, "PAMP", "Pampa Holding S.A.", "ACCIONES");

	private static final InstrumentRef PGR = new InstrumentRef(3, "PGR", "Phoenix Global Resources", "ACCIONES");

	private static final DailyCloses ONLY_PAMP_PRICED = new DailyCloses(
			Map.of(47L, DailyClose.of(new BigDecimal("925.85"), new BigDecimal("921.80"))));

	@Test
	void pricing_keeps_every_instrument_in_the_order_given() {
		List<InstrumentMatch> matches = InstrumentMatch.priced(List.of(PGR, PAMP), ONLY_PAMP_PRICED);

		assertThat(matches).extracting(InstrumentMatch::instrument).containsExactly(PGR, PAMP);
		assertThat(matches.get(0).dailyClose().close()).isEmpty();
		assertThat(matches.get(1).dailyClose().close()).contains(new BigDecimal("925.85"));
	}

}
