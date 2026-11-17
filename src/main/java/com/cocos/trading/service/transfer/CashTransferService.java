package com.cocos.trading.service.transfer;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.ledger.AccountLedger;
import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.persistence.InstrumentRepository;
import com.cocos.trading.persistence.OrderRepository;
import com.cocos.trading.persistence.OrderStatusHistoryRepository;
import com.cocos.trading.persistence.mapper.InstrumentMapper;
import com.cocos.trading.persistence.mapper.OrderMapper;
import com.cocos.trading.service.userwrite.KeyedRequest;
import com.cocos.trading.service.userwrite.UserWrite;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CashTransferService {

	private final UserWrite userWrite;

	private final InstrumentRepository instrumentRepository;

	private final OrderRepository orderRepository;

	private final OrderStatusHistoryRepository orderStatusHistoryRepository;

	private final Clock clock;

	public CashTransferService(UserWrite userWrite, InstrumentRepository instrumentRepository,
			OrderRepository orderRepository, OrderStatusHistoryRepository orderStatusHistoryRepository, Clock clock) {
		this.userWrite = userWrite;
		this.instrumentRepository = instrumentRepository;
		this.orderRepository = orderRepository;
		this.orderStatusHistoryRepository = orderStatusHistoryRepository;
		this.clock = clock;
	}

	public RecordedTransfer transfer(TransferRequest request) {
		RequestedTransfer requested = RequestedTransfer.read(request);
		Optional<KeyedRequest> keyed = KeyedRequest.of(request.idempotencyKey(), () -> TransferFingerprint.of(request));

		return userWrite.write(requested.userId(), keyed, userIdColumn -> recordedTransferOf(requested, userIdColumn),
				this::recordedTransfer);
	}

	private RecordedTransfer recordedTransferOf(RequestedTransfer requested, Integer userIdColumn) {
		InstrumentRef cash = cashInstrument();
		AccountSnapshot snapshot = availabilityOf(userIdColumn);

		return appended(requested.recordedFor(snapshot, cash, now()));
	}

	private InstrumentRef cashInstrument() {
		List<InstrumentRef> cash = instrumentRepository.findByType(InstrumentRef.CASH)
			.stream()
			.map(InstrumentMapper::toInstrumentRef)
			.toList();
		if (cash.size() != 1) {
			throw new IllegalStateException("A cash transfer is recorded on the one " + InstrumentRef.CASH
					+ " instrument, and the deployment's data holds " + cash.size());
		}
		return cash.getFirst();
	}

	private AccountSnapshot availabilityOf(Integer userIdColumn) {
		return new AccountLedger(OrderMapper.toOrders(
				orderRepository.findOrdersOf(userIdColumn, OrderMapper.toStatusColumns(OrderStatus.contributing()))))
			.snapshot();
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

	private RecordedTransfer appended(NewOrder order) {
		Integer orderId = orderRepository.append(order);
		orderStatusHistoryRepository.appendTransitionOf(orderId, order);
		return RecordedTransfer.of(orderId, order);
	}

	private RecordedTransfer recordedTransfer(Integer orderId) {
		NewOrder recorded = orderRepository.findPlacedOrder(orderId)
			.orElseThrow(() -> new IllegalStateException("order " + orderId + " is named by a key but is not stored"));
		return RecordedTransfer.of(orderId, withTheReasonItWasRejectedFor(recorded, orderId));
	}

	private NewOrder withTheReasonItWasRejectedFor(NewOrder recorded, Integer orderId) {
		return orderStatusHistoryRepository.reasonOf(orderId).map(recorded::rejectedFor).orElse(recorded);
	}

}
