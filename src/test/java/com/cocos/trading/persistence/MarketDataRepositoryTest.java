package com.cocos.trading.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;

import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.persistence.mapper.MarketDataMapper;

class MarketDataRepositoryTest extends PersistenceTestBase {

	private static final long PAMP = 47;

	private static final long METR = 54;

	private static final long BMA = 31;

	private static final long PGR = 3;

	private static final long IRCP = 10;

	private static final long ARS = 66;

	private static final long WITH_A_LATER_NULL_CLOSE = 5;

	@Autowired
	private MarketDataRepository marketDataRepository;

	@Test
	void the_latest_row_is_picked_per_instrument() {
		Map<Long, BigDecimal> closes = latestClosesOf(List.of(PAMP, METR, BMA));

		assertThat(closes.get(PAMP)).isEqualByComparingTo("925.85");
		assertThat(closes.get(METR)).isEqualByComparingTo("229.50");
		assertThat(closes.get(BMA)).isEqualByComparingTo("1502.80");
	}

	@Test
	void the_previous_close_comes_from_the_same_latest_row() {
		assertThat(latestRowOf(PAMP).getPreviousClose()).isEqualByComparingTo("921.80");
	}

	@Test
	@Sql("/db/fixtures/later-close-for-pamp.sql")
	@Sql(scripts = "/db/fixtures/later-close-for-pamp-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_METHOD)
	void the_latest_row_is_resolved_per_instrument_not_at_one_shared_date() {
		assertThat(latestRowOf(PAMP).getClose()).isEqualByComparingTo("999.99");
		assertThat(latestRowOf(PAMP).getPreviousClose()).isEqualByComparingTo("900.00");
		assertThat(latestRowOf(METR).getClose()).isEqualByComparingTo("229.50");
	}

	@Test
	void an_instrument_with_no_row_is_absent() {
		assertThat(latestClosesOf(List.of(PGR, IRCP, ARS))).isEmpty();
	}

	@Test
	void the_unpriced_instruments_in_the_seed_are_pgr_ircp_and_ars() {
		List<Long> every = LongStream.rangeClosed(1, 66).boxed().toList();

		Map<Long, BigDecimal> closes = latestClosesOf(every);

		assertThat(closes).hasSize(63).doesNotContainKeys(PGR, IRCP, ARS);
	}

	@Test
	@Sql("/db/fixtures/null-close.sql")
	@Sql(scripts = "/db/fixtures/null-close-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_METHOD)
	void a_latest_row_with_a_null_close_counts_as_no_known_price() {
		List<LatestMarketData> rows = marketDataRepository
			.findLatestMarketData(MarketDataMapper.toInstrumentIdColumns(List.of(WITH_A_LATER_NULL_CLOSE)));

		assertThat(rows).singleElement().satisfies(row -> assertThat(row.getClose()).isNull());
		assertThat(MarketDataMapper.toCloses(rows).of(instrument(WITH_A_LATER_NULL_CLOSE))).isEmpty();
	}

	@Test
	void asking_for_nothing_reads_nothing() {
		assertThat(latestClosesOf(List.of())).isEmpty();
	}

	private Map<Long, BigDecimal> latestClosesOf(List<Long> instrumentIds) {
		Map<Long, BigDecimal> closes = new HashMap<>();
		marketDataRepository.findLatestMarketData(MarketDataMapper.toInstrumentIdColumns(instrumentIds))
			.forEach(row -> closes.put(row.getInstrumentId().longValue(), row.getClose()));
		return closes;
	}

	private LatestMarketData latestRowOf(long instrumentId) {
		List<LatestMarketData> rows = marketDataRepository
			.findLatestMarketData(MarketDataMapper.toInstrumentIdColumns(List.of(instrumentId)));
		assertThat(rows).hasSize(1);
		return rows.getFirst();
	}

	private static InstrumentRef instrument(long instrumentId) {
		return new InstrumentRef(instrumentId, "TICKER", "Name", "ACCIONES");
	}

}
