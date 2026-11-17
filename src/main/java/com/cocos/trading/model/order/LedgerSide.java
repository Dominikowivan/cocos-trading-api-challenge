package com.cocos.trading.model.order;

import java.math.BigDecimal;

public sealed interface LedgerSide permits OrderSide, CashMovement {

	BigDecimal INFLOW = BigDecimal.ONE;

	BigDecimal OUTFLOW = BigDecimal.ONE.negate();

	BigDecimal cashSign();

	BigDecimal positionSign();

	String name();

}
