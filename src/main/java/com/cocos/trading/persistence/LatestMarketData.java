package com.cocos.trading.persistence;

import java.math.BigDecimal;

public interface LatestMarketData {

	Integer getInstrumentId();

	BigDecimal getClose();

	BigDecimal getPreviousClose();

}
