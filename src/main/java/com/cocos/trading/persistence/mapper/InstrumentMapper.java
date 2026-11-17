package com.cocos.trading.persistence.mapper;

import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.persistence.entity.InstrumentEntity;

public final class InstrumentMapper {

	private InstrumentMapper() {
	}

	public static InstrumentRef toInstrumentRef(InstrumentEntity instrument) {
		return new InstrumentRef(instrument.getId(), instrument.getTicker(), instrument.getName(),
				instrument.getType());
	}

}
