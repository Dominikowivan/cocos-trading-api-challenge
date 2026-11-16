package com.cocos.trading.persistence.mapper;

import org.springframework.data.domain.Page;

import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.persistence.entity.InstrumentEntity;

public final class InstrumentSearchMapper {

	private InstrumentSearchMapper() {
	}

	public static Page<InstrumentRef> toInstrumentRefs(Page<InstrumentEntity> instruments) {
		return instruments.map(InstrumentSearchMapper::toInstrumentRef);
	}

	private static InstrumentRef toInstrumentRef(InstrumentEntity instrument) {
		return new InstrumentRef(instrument.getId(), instrument.getTicker(), instrument.getName(),
				instrument.getType());
	}

}
