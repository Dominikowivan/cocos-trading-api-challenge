package com.cocos.trading.service.submission;

import static com.cocos.trading.service.submission.InvalidSubmissionException.refusing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.cocos.trading.model.instrument.Closes;
import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.ledger.AccountLedger;
import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.submission.OrderDecision;
import com.cocos.trading.model.submission.PricedSubmission;
import com.cocos.trading.model.submission.Submission;
import com.cocos.trading.model.user.UnknownUserException;
import com.cocos.trading.model.user.UserId;
import com.cocos.trading.persistence.InstrumentRepository;
import com.cocos.trading.persistence.MarketDataRepository;
import com.cocos.trading.persistence.OrderIdempotencyRepository;
import com.cocos.trading.persistence.OrderRepository;
import com.cocos.trading.persistence.OrderStatusHistoryRepository;
import com.cocos.trading.persistence.UserRepository;
import com.cocos.trading.persistence.mapper.InstrumentMapper;
import com.cocos.trading.persistence.mapper.MarketDataMapper;
import com.cocos.trading.persistence.mapper.OrderMapper;
import com.cocos.trading.persistence.mapper.UserMapper;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class OrderSubmissionService {

	private static final int NOTHING_WRITTEN = 0;

	private final UserRepository userRepository;

	private final InstrumentRepository instrumentRepository;

	private final MarketDataRepository marketDataRepository;

	private final OrderRepository orderRepository;

	private final OrderStatusHistoryRepository orderStatusHistoryRepository;

	private final OrderIdempotencyRepository orderIdempotencyRepository;

	private final Clock clock;

	public OrderSubmissionService(UserRepository userRepository, InstrumentRepository instrumentRepository,
			MarketDataRepository marketDataRepository, OrderRepository orderRepository,
			OrderStatusHistoryRepository orderStatusHistoryRepository,
			OrderIdempotencyRepository orderIdempotencyRepository, Clock clock) {
		this.userRepository = userRepository;
		this.instrumentRepository = instrumentRepository;
		this.marketDataRepository = marketDataRepository;
		this.orderRepository = orderRepository;
		this.orderStatusHistoryRepository = orderStatusHistoryRepository;
		this.orderIdempotencyRepository = orderIdempotencyRepository;
		this.clock = clock;
	}

	public SubmittedOrder submit(SubmissionRequest request) {
		RequestedSubmission requested = RequestedSubmission.read(request);
		Integer userIdColumn = existingUser(requested.userId());

		return requested.idempotencyKey()
			.map(key -> theOneOutcomeUnder(key, requested, userIdColumn))
			.orElseGet(() -> placedOrderOf(requested, userIdColumn));
	}

	private SubmittedOrder theOneOutcomeUnder(IdempotencyKey key, RequestedSubmission requested, Integer userIdColumn) {
		if (claimed(key, requested, userIdColumn)) {
			return attachedTo(key, userIdColumn, placedOrderOf(requested, userIdColumn));
		}
		return theOutcomeAlreadyRecordedFor(key, requested, userIdColumn);
	}

	private boolean claimed(IdempotencyKey key, RequestedSubmission requested, Integer userIdColumn) {
		return orderIdempotencyRepository.claim(userIdColumn, key.value(), requested.fingerprint().hex(),
				now()) != NOTHING_WRITTEN;
	}

	private SubmittedOrder attachedTo(IdempotencyKey key, Integer userIdColumn, SubmittedOrder order) {
		orderIdempotencyRepository.attachOrderTo(userIdColumn, key.value(), Math.toIntExact(order.orderId()));
		return order;
	}

	private SubmittedOrder theOutcomeAlreadyRecordedFor(IdempotencyKey key, RequestedSubmission requested,
			Integer userIdColumn) {
		if (!requested.fingerprint().matches(recordedFingerprintOf(key, userIdColumn))) {
			throw new IdempotencyConflictException(key.value());
		}
		return recordedOrder(recordedOrderIdOf(key, userIdColumn));
	}

	private String recordedFingerprintOf(IdempotencyKey key, Integer userIdColumn) {
		return orderIdempotencyRepository.fingerprintOf(userIdColumn, key.value())
			.orElseThrow(() -> new IllegalStateException(
					"the key " + key.value() + " was claimed by another request that then left no row"));
	}

	private Integer recordedOrderIdOf(IdempotencyKey key, Integer userIdColumn) {
		return orderIdempotencyRepository.orderIdOf(userIdColumn, key.value())
			.orElseThrow(() -> new IllegalStateException(
					"the key " + key.value() + " names no order, which a committed claim always does"));
	}

	private SubmittedOrder recordedOrder(Integer orderId) {
		NewOrder recorded = orderRepository.findPlacedOrder(orderId)
			.orElseThrow(() -> new IllegalStateException("order " + orderId + " is named by a key but is not stored"));
		return SubmittedOrder.of(orderId, withTheReasonItWasRejectedFor(recorded, orderId));
	}

	private NewOrder withTheReasonItWasRejectedFor(NewOrder recorded, Integer orderId) {
		return orderStatusHistoryRepository.reasonOf(orderId).map(recorded::rejectedFor).orElse(recorded);
	}

	private SubmittedOrder placedOrderOf(RequestedSubmission requested, Integer userIdColumn) {
		lockUser(userIdColumn, requested.userId());

		PricedSubmission submission = priceGoverning(submissionOf(requested));

		OrderDecision decision = refusing(() -> submission.decide(availabilityOf(userIdColumn)));

		NewOrder order = submission.placedBy(requested.userId(), decision, now());
		Integer orderId = orderRepository.append(order);
		orderStatusHistoryRepository.appendTransitionOf(orderId, order);
		return SubmittedOrder.of(orderId, order);
	}

	private Integer existingUser(UserId userId) {
		return UserMapper.toIdColumn(userId)
			.filter(userRepository::existsById)
			.orElseThrow(() -> new UnknownUserException(userId));
	}

	private void lockUser(Integer userIdColumn, UserId userId) {
		if (userRepository.lockById(userIdColumn).isEmpty()) {
			throw new UnknownUserException(userId);
		}
	}

	private Submission submissionOf(RequestedSubmission requested) {
		return requested.tradedAs(tradedInstrument(requested.instrumentIdColumn()));
	}

	private InstrumentRef tradedInstrument(Integer instrumentIdColumn) {
		return instrumentRepository.findById(instrumentIdColumn)
			.map(InstrumentMapper::toInstrumentRef)
			.orElseThrow(() -> new InvalidSubmissionException("No instrument with id " + instrumentIdColumn));
	}

	private PricedSubmission priceGoverning(Submission submission) {
		return submission.priceGovernedBy(() -> latestCloseOf(submission.instrument()))
			.orElseThrow(() -> new InvalidSubmissionException(
					"Instrument " + submission.instrument().instrumentId() + " (" + submission.instrument().ticker()
							+ ") has no market price, so a MARKET order has no execution price"));
	}

	private Closes latestCloseOf(InstrumentRef instrument) {
		return MarketDataMapper.toCloses(marketDataRepository
			.findLatestMarketData(MarketDataMapper.toInstrumentIdColumns(List.of(instrument.instrumentId()))));
	}

	private AccountSnapshot availabilityOf(Integer userIdColumn) {
		return new AccountLedger(OrderMapper.toOrders(
				orderRepository.findOrdersOf(userIdColumn, OrderMapper.toStatusColumns(OrderStatus.contributing()))))
			.snapshot();
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

}
