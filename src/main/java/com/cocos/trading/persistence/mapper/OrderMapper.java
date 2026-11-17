package com.cocos.trading.persistence.mapper;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cocos.trading.model.Price;
import com.cocos.trading.model.order.CashMovement;
import com.cocos.trading.model.order.LedgerSide;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.Order;
import com.cocos.trading.model.order.OrderKind;
import com.cocos.trading.model.order.OrderSide;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.order.RejectionReason;
import com.cocos.trading.model.user.UserId;
import com.cocos.trading.persistence.entity.OrderEntity;
import com.cocos.trading.persistence.entity.OrderStatusHistoryEntity;

public final class OrderMapper {

	private OrderMapper() {
	}

	public static List<Order> toOrders(List<OrderEntity> orders) {
		return orders.stream().map(OrderMapper::toOrder).toList();
	}

	public static List<String> toStatusColumns(Collection<OrderStatus> statuses) {
		return statuses.stream().map(Enum::name).toList();
	}

	public static OrderEntity toNewOrder(NewOrder order) {
		return OrderEntity.newOrder()
			.ofUser(toUserIdColumn(order))
			.inInstrument(toInstrumentIdColumn(order))
			.ofSize(toSizeColumn(order))
			.atPrice(order.price().amount())
			.onSide(order.side().name())
			.withStatus(order.status().name())
			.ofType(order.kind().name())
			.placedAt(order.occurredAt());
	}

	public static NewOrder toPlacedOrder(OrderEntity order) {
		return new NewOrder(new UserId(order.getUserId()), InstrumentMapper.toInstrumentRef(order.getInstrument()),
				toLedgerSide(order.getSide()), OrderKind.valueOf(order.getType()),
				OrderStatus.valueOf(order.getStatus()), BigDecimal.valueOf(order.getSize()), Price.of(order.getPrice()),
				Optional.empty(), order.getDatetime());
	}

	public static OrderStatusHistoryEntity toNewTransition(Integer orderId, NewOrder order) {
		return OrderStatusHistoryEntity.newTransition(orderId, order.status().name(), toReasonColumn(order),
				order.occurredAt());
	}

	private static Order toOrder(OrderEntity order) {
		return new Order(order.getId(), InstrumentMapper.toInstrumentRef(order.getInstrument()),
				toLedgerSide(order.getSide()), OrderStatus.valueOf(order.getStatus()),
				OrderKind.valueOf(order.getType()), BigDecimal.valueOf(order.getSize()), order.getPrice(),
				order.getDatetime());
	}

	private static LedgerSide toLedgerSide(String side) {
		for (CashMovement movement : CashMovement.values()) {
			if (movement.name().equals(side)) {
				return movement;
			}
		}
		return OrderSide.valueOf(side);
	}

	private static Integer toUserIdColumn(NewOrder order) {
		return Math.toIntExact(order.userId().value());
	}

	private static Integer toInstrumentIdColumn(NewOrder order) {
		return Math.toIntExact(order.instrument().instrumentId());
	}

	private static int toSizeColumn(NewOrder order) {
		return order.size().intValueExact();
	}

	private static String toReasonColumn(NewOrder order) {
		return order.rejectionReason().map(RejectionReason::name).orElse(null);
	}

}
