package com.cocos.trading.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.persistence.entity.OrderStatusHistoryEntity;
import com.cocos.trading.persistence.mapper.OrderMapper;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, Integer> {

	List<OrderStatusHistoryEntity> findByOrderId(Integer orderId);

	default void appendTransitionOf(Integer orderId, NewOrder order) {
		save(OrderMapper.toNewTransition(orderId, order));
	}

}
