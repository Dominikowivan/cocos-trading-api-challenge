package com.cocos.trading.service.search;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.cocos.trading.model.instrument.DailyCloses;
import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.persistence.InstrumentRepository;
import com.cocos.trading.persistence.MarketDataRepository;
import com.cocos.trading.persistence.mapper.InstrumentSearchMapper;
import com.cocos.trading.persistence.mapper.MarketDataMapper;

@Service
@Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
public class InstrumentSearchService {

	private final InstrumentRepository instrumentRepository;

	private final MarketDataRepository marketDataRepository;

	private final SearchLimits limits;

	public InstrumentSearchService(InstrumentRepository instrumentRepository, MarketDataRepository marketDataRepository,
			SearchProperties searchProperties) {
		this.instrumentRepository = instrumentRepository;
		this.marketDataRepository = marketDataRepository;
		this.limits = searchProperties.limits();
	}

	public InstrumentMatches search(SearchRequest request) {
		SearchQuery query = SearchQuery.of(request.query(), limits);
		SearchPage page = SearchPage.of(request.page(), request.size(), limits);
		Page<InstrumentRef> instruments = instrumentsMatching(query, page);

		return new InstrumentMatches(query,
				InstrumentMatch.priced(instruments.getContent(), dailyClosesOf(instruments.getContent())), page,
				instruments.getTotalElements());
	}

	private Page<InstrumentRef> instrumentsMatching(SearchQuery query, SearchPage page) {
		return InstrumentSearchMapper.toInstrumentRefs(
				instrumentRepository.searchNonCash(query.text(), PageRequest.of(page.number(), page.size())));
	}

	private DailyCloses dailyClosesOf(List<InstrumentRef> instruments) {
		List<Long> instrumentIds = instruments.stream().map(InstrumentRef::instrumentId).toList();
		return MarketDataMapper.toDailyCloses(
				marketDataRepository.findLatestMarketData(MarketDataMapper.toInstrumentIdColumns(instrumentIds)));
	}

}
