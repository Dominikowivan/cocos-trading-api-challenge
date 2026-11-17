package com.cocos.trading.service.userwrite;

import org.springframework.transaction.support.TransactionSynchronizationManager;

final class CallersTransaction {

	private CallersTransaction() {
	}

	static void require(String why) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(why);
		}
	}

}
