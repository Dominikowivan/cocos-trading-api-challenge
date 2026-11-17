package com.cocos.trading.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cocos.trading.model.order.CancellationDecision;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.order.RecordedTransition;
import com.cocos.trading.persistence.entity.OrderStatusHistoryEntity;
import com.cocos.trading.persistence.mapper.OrderMapper;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, Integer> {

	List<OrderStatusHistoryEntity> findByOrderId(Integer orderId);

	Optional<OrderStatusHistoryEntity> findFirstByOrderIdOrderByIdAsc(Integer orderId);

	Optional<OrderStatusHistoryEntity> findFirstByOrderIdAndStatusOrderByIdAsc(Integer orderId, String status);

	default Optional<RecordedTransition> firstTransitionOf(Integer orderId) {
		return findFirstByOrderIdOrderByIdAsc(orderId).map(OrderMapper::toRecordedTransition);
	}

	default Optional<RecordedTransition> cancellationOf(Integer orderId) {
		return findFirstByOrderIdAndStatusOrderByIdAsc(orderId, OrderStatus.CANCELLED.name())
			.map(OrderMapper::toRecordedTransition);
	}

	default void appendTransitionOf(Integer orderId, NewOrder order) {
		save(OrderMapper.toNewTransition(orderId, order));
	}

	default void appendCancellationOf(Integer orderId, CancellationDecision.Cancellation cancellation) {
		save(OrderMapper.toCancellationTransition(orderId, cancellation));
	}

}
