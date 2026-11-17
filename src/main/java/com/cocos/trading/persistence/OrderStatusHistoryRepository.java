package com.cocos.trading.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.RejectionReason;
import com.cocos.trading.persistence.entity.OrderStatusHistoryEntity;
import com.cocos.trading.persistence.mapper.OrderMapper;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, Integer> {

	List<OrderStatusHistoryEntity> findByOrderId(Integer orderId);

	default Optional<RejectionReason> reasonOf(Integer orderId) {
		return findByOrderId(orderId).stream()
			.map(OrderStatusHistoryEntity::getReason)
			.filter(Objects::nonNull)
			.map(RejectionReason::valueOf)
			.findFirst();
	}

	default void appendTransitionOf(Integer orderId, NewOrder order) {
		save(OrderMapper.toNewTransition(orderId, order));
	}

}
