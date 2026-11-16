package com.cocos.trading.persistence;

import java.math.BigDecimal;

public interface LatestClose {

	Integer getInstrumentId();

	BigDecimal getClose();

}
