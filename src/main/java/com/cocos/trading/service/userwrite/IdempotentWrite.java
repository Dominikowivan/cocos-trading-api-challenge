package com.cocos.trading.service.userwrite;

import java.util.function.IntFunction;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.cocos.trading.model.user.UnknownUserException;
import com.cocos.trading.model.user.UserId;
import com.cocos.trading.persistence.OrderIdempotencyRepository;
import com.cocos.trading.persistence.mapper.UserMapper;

@Component
public class IdempotentWrite {

	private final OrderIdempotencyRepository orderIdempotencyRepository;

	private final LedgerClock clock;

	public IdempotentWrite(OrderIdempotencyRepository orderIdempotencyRepository, LedgerClock clock) {
		this.orderIdempotencyRepository = orderIdempotencyRepository;
		this.clock = clock;
	}

	public <T extends WrittenOrder> T once(UserId userId, KeyedRequest request, Supplier<T> work,
			IntFunction<T> replay) {
		CallersTransaction
			.require("An idempotency claim runs inside the use case's transaction, so a refused request rolls it back");
		Integer userIdColumn = UserMapper.toIdColumn(userId).orElseThrow(() -> new UnknownUserException(userId));

		if (claimed(request, userIdColumn)) {
			return attachedTo(request, userIdColumn, work.get());
		}
		return theOutcomeAlreadyRecordedFor(request, userIdColumn, replay);
	}

	private boolean claimed(KeyedRequest request, Integer userIdColumn) {
		return orderIdempotencyRepository.claim(userIdColumn, request.keyValue(), request.fingerprintHex(),
				clock.now());
	}

	private <T extends WrittenOrder> T attachedTo(KeyedRequest request, Integer userIdColumn, T order) {
		orderIdempotencyRepository.attachOrderTo(userIdColumn, request.keyValue(), Math.toIntExact(order.orderId()));
		return order;
	}

	private <T extends WrittenOrder> T theOutcomeAlreadyRecordedFor(KeyedRequest request, Integer userIdColumn,
			IntFunction<T> replay) {
		if (!request.wasSentBefore(recordedFingerprintOf(request, userIdColumn))) {
			throw new IdempotencyConflictException(request.keyValue());
		}
		return replay.apply(recordedOrderIdOf(request, userIdColumn));
	}

	private String recordedFingerprintOf(KeyedRequest request, Integer userIdColumn) {
		return orderIdempotencyRepository.fingerprintOf(userIdColumn, request.keyValue())
			.orElseThrow(() -> new IllegalStateException(
					"the key " + request.keyValue() + " was claimed by another request that then left no row"));
	}

	private Integer recordedOrderIdOf(KeyedRequest request, Integer userIdColumn) {
		return orderIdempotencyRepository.orderIdOf(userIdColumn, request.keyValue())
			.orElseThrow(() -> new IllegalStateException(
					"the key " + request.keyValue() + " names no order, which a committed claim always does"));
	}

}
