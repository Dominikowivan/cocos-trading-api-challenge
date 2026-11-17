package com.cocos.trading.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.persistence.entity.OrderEntity;
import com.cocos.trading.persistence.mapper.OrderMapper;

public interface OrderRepository extends JpaRepository<OrderEntity, Integer> {

	@Query("""
			select o from OrderEntity o join fetch o.instrument
			where o.userId = :userId and o.status in :statuses
			order by o.datetime asc, o.id asc
			""")
	List<OrderEntity> findOrdersOf(@Param("userId") Integer userId, @Param("statuses") Collection<String> statuses);

	@Query("""
			select o from OrderEntity o join fetch o.instrument
			where o.id = :orderId and o.userId = :userId
			""")
	Optional<OrderEntity> findOrderOfUser(@Param("orderId") Integer orderId, @Param("userId") Integer userId);

	@Modifying
	@Query("update OrderEntity o set o.status = :to where o.id = :orderId and o.status = :from")
	int updateStatus(@Param("orderId") Integer orderId, @Param("from") String from, @Param("to") String to);

	default Optional<NewOrder> findPlacedOrder(Integer orderId) {
		return findById(orderId).map(OrderMapper::toPlacedOrder);
	}

	default Optional<NewOrder> findPlacedOrderOf(Integer userId, Integer orderId) {
		return findOrderOfUser(orderId, userId).map(OrderMapper::toPlacedOrder);
	}

	default boolean moveStatus(Integer orderId, OrderStatus from, OrderStatus to) {
		return updateStatus(orderId, from.name(), to.name()) == 1;
	}

	default Integer append(NewOrder order) {
		return save(OrderMapper.toNewOrder(order)).getId();
	}

}
