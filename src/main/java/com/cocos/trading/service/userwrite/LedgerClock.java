package com.cocos.trading.service.userwrite;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

@Component
public class LedgerClock {

	private static final ChronoUnit LEDGER_PRECISION = ChronoUnit.MICROS;

	private final Clock clock;

	public LedgerClock(Clock clock) {
		this.clock = clock;
	}

	public LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(LEDGER_PRECISION);
	}

}
