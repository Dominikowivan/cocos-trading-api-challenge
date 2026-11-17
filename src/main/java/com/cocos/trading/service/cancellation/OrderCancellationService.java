package com.cocos.trading.service.cancellation;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.cocos.trading.model.order.CancellationDecision;
import com.cocos.trading.model.order.CancellationRule;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.RecordedTransition;
import com.cocos.trading.persistence.OrderRepository;
import com.cocos.trading.persistence.OrderStatusHistoryRepository;
import com.cocos.trading.persistence.mapper.OrderMapper;
import com.cocos.trading.service.userwrite.KeyedRequest;
import com.cocos.trading.service.userwrite.UserWrite;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class OrderCancellationService {

	private final UserWrite userWrite;

	private final OrderRepository orderRepository;

	private final OrderStatusHistoryRepository orderStatusHistoryRepository;

	private final Clock clock;

	public OrderCancellationService(UserWrite userWrite, OrderRepository orderRepository,
			OrderStatusHistoryRepository orderStatusHistoryRepository, Clock clock) {
		this.userWrite = userWrite;
		this.orderRepository = orderRepository;
		this.orderStatusHistoryRepository = orderStatusHistoryRepository;
		this.clock = clock;
	}

	public CancelledOrder cancel(CancellationRequest request) {
		RequestedCancellation requested = RequestedCancellation.read(request);
		Optional<KeyedRequest> keyed = KeyedRequest.of(request.idempotencyKey(),
				() -> CancellationFingerprint.of(request));

		return userWrite.write(requested.userId(), keyed, userIdColumn -> cancelledOrderOf(requested, userIdColumn),
				this::recordedCancellation);
	}

	private CancelledOrder cancelledOrderOf(RequestedCancellation requested, Integer userIdColumn) {
		Integer orderIdColumn = orderIdColumnOf(requested);
		NewOrder order = theUsersOrder(requested, userIdColumn, orderIdColumn);
		CancellationDecision.Cancellation cancellation = cancellationOf(requested, order);

		recordedInTheLedger(orderIdColumn, order, cancellation);
		return CancelledOrder.of(orderIdColumn, order, cancellation.status());
	}

	private Integer orderIdColumnOf(RequestedCancellation requested) {
		return OrderMapper.toIdColumn(requested.orderId()).orElseThrow(() -> unknownOrder(requested));
	}

	private NewOrder theUsersOrder(RequestedCancellation requested, Integer userIdColumn, Integer orderIdColumn) {
		return orderRepository.findPlacedOrderOf(userIdColumn, orderIdColumn)
			.orElseThrow(() -> unknownOrder(requested));
	}

	private CancellationDecision.Cancellation cancellationOf(RequestedCancellation requested, NewOrder order) {
		return switch (CancellationRule.decide(requested.orderId(), order.status(), now())) {
			case CancellationDecision.Cancellation cancellation -> cancellation;
			case CancellationDecision.NotCancellable refusal -> throw OrderNotCancellableException.of(refusal);
		};
	}

	private void recordedInTheLedger(Integer orderIdColumn, NewOrder order,
			CancellationDecision.Cancellation cancellation) {
		if (!orderRepository.moveStatus(orderIdColumn, order.status(), cancellation.status())) {
			throw OrderNotCancellableException.leftNewBeforeItsTransition(orderIdColumn);
		}
		orderStatusHistoryRepository.appendCancellationOf(orderIdColumn, cancellation);
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

	private CancelledOrder recordedCancellation(Integer orderId) {
		NewOrder recorded = orderRepository.findPlacedOrder(orderId)
			.orElseThrow(() -> new IllegalStateException("order " + orderId + " is named by a key but is not stored"));
		return CancelledOrder.of(orderId, recorded, cancellationRecordedFor(orderId).status());
	}

	private RecordedTransition cancellationRecordedFor(Integer orderId) {
		return orderStatusHistoryRepository.cancellationOf(orderId)
			.orElseThrow(() -> new IllegalStateException(
					"order " + orderId + " is named by a cancellation's key but was never cancelled"));
	}

	private static UnknownOrderException unknownOrder(RequestedCancellation requested) {
		return new UnknownOrderException(requested.orderId(), requested.userId());
	}

}
