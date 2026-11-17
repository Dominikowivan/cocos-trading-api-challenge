package com.cocos.trading.service.submission;

import static com.cocos.trading.service.submission.InvalidSubmissionException.refusing;

import java.math.BigDecimal;
import java.util.Optional;

import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.order.OrderKind;
import com.cocos.trading.model.order.OrderSide;
import com.cocos.trading.model.submission.Submission;
import com.cocos.trading.model.submission.TradeQuantity;
import com.cocos.trading.model.user.UserId;

final class RequestedSubmission {

	private final UserId userId;

	private final Integer instrumentIdColumn;

	private final OrderKind kind;

	private final OrderSide side;

	private final TradeQuantity quantity;

	private final BigDecimal limitPrice;

	private final IdempotencyKey key;

	private final RequestFingerprint fingerprint;

	private RequestedSubmission(UserId userId, Integer instrumentIdColumn, OrderKind kind, OrderSide side,
			TradeQuantity quantity, BigDecimal limitPrice, IdempotencyKey key, RequestFingerprint fingerprint) {
		this.userId = userId;
		this.instrumentIdColumn = instrumentIdColumn;
		this.kind = kind;
		this.side = side;
		this.quantity = quantity;
		this.limitPrice = limitPrice;
		this.key = key;
		this.fingerprint = fingerprint;
	}

	static RequestedSubmission read(SubmissionRequest request) {
		return new RequestedSubmission(new UserId(request.userId()), instrumentIdColumnOf(request.instrumentId()),
				kindOf(request.type()), sideOf(request.side()), quantityOf(request), request.price(),
				keyOf(request.idempotencyKey()), RequestFingerprint.of(request));
	}

	UserId userId() {
		return userId;
	}

	Integer instrumentIdColumn() {
		return instrumentIdColumn;
	}

	Optional<IdempotencyKey> idempotencyKey() {
		return Optional.ofNullable(key);
	}

	RequestFingerprint fingerprint() {
		return fingerprint;
	}

	Submission tradedAs(InstrumentRef instrument) {
		return refusing(() -> Submission.of(instrument, side, kind, quantity, limitPrice));
	}

	private static Integer instrumentIdColumnOf(Long instrumentId) {
		if (instrumentId == null) {
			throw new InvalidSubmissionException("An order names the instrument it trades in 'instrumentId'");
		}
		if (instrumentId < 1 || instrumentId > Integer.MAX_VALUE) {
			throw new InvalidSubmissionException("No instrument with id " + instrumentId);
		}
		return instrumentId.intValue();
	}

	private static OrderKind kindOf(String type) {
		if (type == null) {
			throw new InvalidSubmissionException("An order states its type in 'type': MARKET or LIMIT");
		}
		try {
			return OrderKind.valueOf(type);
		}
		catch (IllegalArgumentException unknown) {
			throw new InvalidSubmissionException("Unknown type '" + type + "': an order is MARKET or LIMIT", unknown);
		}
	}

	private static OrderSide sideOf(String side) {
		if (side == null) {
			throw new InvalidSubmissionException("An order states its side in 'side': BUY or SELL");
		}
		try {
			return OrderSide.valueOf(side);
		}
		catch (IllegalArgumentException unknown) {
			throw new InvalidSubmissionException("Unknown side '" + side + "': an order is BUY or SELL", unknown);
		}
	}

	private static TradeQuantity quantityOf(SubmissionRequest request) {
		return refusing(() -> TradeQuantity.of(request.size(), request.amount()));
	}

	private static IdempotencyKey keyOf(String key) {
		return key == null ? null : refusing(() -> new IdempotencyKey(key));
	}

}
