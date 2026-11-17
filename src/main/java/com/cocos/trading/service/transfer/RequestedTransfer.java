package com.cocos.trading.service.transfer;

import static com.cocos.trading.service.transfer.InvalidTransferException.refusing;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.order.CashMovement;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.transfer.CashTransfer;
import com.cocos.trading.model.user.UserId;

final class RequestedTransfer {

	private final UserId userId;

	private final CashTransfer transfer;

	private RequestedTransfer(UserId userId, CashTransfer transfer) {
		this.userId = userId;
		this.transfer = transfer;
	}

	static RequestedTransfer read(TransferRequest request) {
		requireOnlyTheTransfersFields(request.otherFields());
		CashMovement direction = directionOf(request.side());
		return new RequestedTransfer(new UserId(request.userId()),
				refusing(() -> CashTransfer.of(direction, request.amount())));
	}

	UserId userId() {
		return userId;
	}

	NewOrder recordedFor(AccountSnapshot snapshot, InstrumentRef cash, LocalDateTime at) {
		return transfer.recordedFor(snapshot, cash, userId, at);
	}

	private static void requireOnlyTheTransfersFields(Set<String> otherFields) {
		if (!otherFields.isEmpty()) {
			throw new InvalidTransferException(
					"A cash transfer carries only 'side' and 'amount', not " + named(otherFields));
		}
	}

	private static CashMovement directionOf(String side) {
		if (side == null) {
			throw new InvalidTransferException("A cash transfer states its side in 'side': CASH_IN or CASH_OUT");
		}
		try {
			return CashMovement.valueOf(side);
		}
		catch (IllegalArgumentException unknown) {
			throw new InvalidTransferException("Unknown side '" + side + "': a cash transfer is CASH_IN or CASH_OUT",
					unknown);
		}
	}

	private static String named(Set<String> fields) {
		return fields.stream().sorted().map(field -> "'" + field + "'").collect(Collectors.joining(", "));
	}

}
