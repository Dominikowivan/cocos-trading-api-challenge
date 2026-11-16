package com.cocos.trading.persistence.mapper;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.order.Order;
import com.cocos.trading.model.order.OrderKind;
import com.cocos.trading.model.order.OrderSide;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.persistence.entity.InstrumentEntity;
import com.cocos.trading.persistence.entity.OrderEntity;

public final class OrderMapper {

	private OrderMapper() {
	}

	public static List<Order> toOrders(List<OrderEntity> orders) {
		return orders.stream().map(OrderMapper::toOrder).toList();
	}

	public static List<String> toStatusColumns(Collection<OrderStatus> statuses) {
		return statuses.stream().map(Enum::name).toList();
	}

	private static Order toOrder(OrderEntity order) {
		return new Order(order.getId(), toInstrumentRef(order.getInstrument()),
				OrderSide.valueOf(order.getSide()), OrderStatus.valueOf(order.getStatus()),
				OrderKind.valueOf(order.getType()), BigDecimal.valueOf(order.getSize()), order.getPrice(),
				order.getDatetime());
	}

	private static InstrumentRef toInstrumentRef(InstrumentEntity instrument) {
		return new InstrumentRef(instrument.getId(), instrument.getTicker(), instrument.getName(),
				instrument.getType());
	}

}
