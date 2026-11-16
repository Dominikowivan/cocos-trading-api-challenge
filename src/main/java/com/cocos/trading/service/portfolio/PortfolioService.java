package com.cocos.trading.service.portfolio;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.cocos.trading.model.ledger.AccountLedger;
import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.order.Order;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.portfolio.Closes;
import com.cocos.trading.model.portfolio.Portfolio;
import com.cocos.trading.model.user.UserId;
import com.cocos.trading.persistence.MarketDataRepository;
import com.cocos.trading.persistence.OrderRepository;
import com.cocos.trading.persistence.UserRepository;
import com.cocos.trading.persistence.mapper.MarketDataMapper;
import com.cocos.trading.persistence.mapper.OrderMapper;
import com.cocos.trading.persistence.mapper.UserMapper;

@Service
@Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
public class PortfolioService {

	private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

	private final UserRepository userRepository;

	private final OrderRepository orderRepository;

	private final MarketDataRepository marketDataRepository;

	public PortfolioService(UserRepository userRepository, OrderRepository orderRepository,
			MarketDataRepository marketDataRepository) {
		this.userRepository = userRepository;
		this.orderRepository = orderRepository;
		this.marketDataRepository = marketDataRepository;
	}

	public Portfolio portfolioOf(UserId userId) {
		Integer userIdColumn = requireExisting(userId);

		AccountSnapshot snapshot = new AccountLedger(ordersOf(userIdColumn)).snapshot();
		Portfolio portfolio = Portfolio.valued(snapshot, latestClosesOf(snapshot));

		reportAnomalies(userId, portfolio);
		return portfolio;
	}

	private Integer requireExisting(UserId userId) {
		return UserMapper.toIdColumn(userId)
			.filter(userRepository::existsById)
			.orElseThrow(() -> new UnknownUserException(userId));
	}

	private List<Order> ordersOf(Integer userIdColumn) {
		return OrderMapper.toOrders(
				orderRepository.findOrdersOf(userIdColumn, OrderMapper.toStatusColumns(OrderStatus.contributing())));
	}

	private Closes latestClosesOf(AccountSnapshot snapshot) {
		return MarketDataMapper.toCloses(marketDataRepository
			.findLatestMarketData(MarketDataMapper.toInstrumentIdColumns(snapshot.heldInstrumentIds())));
	}

	private static void reportAnomalies(UserId userId, Portfolio portfolio) {
		portfolio.shortPositions()
			.forEach(position -> log.error("short position served as-is: userId={} ticker={} quantity={}", userId,
					position.instrument().ticker(), position.quantity()));
		portfolio.unpricedPositions()
			.forEach(position -> log.warn("no market data for held instrument: userId={} ticker={}", userId,
					position.instrument().ticker()));
	}

}
