package com.cocos.trading.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cocos.trading.persistence.entity.OrderEntity;

public interface OrderRepository extends JpaRepository<OrderEntity, Integer> {

	@Query("""
			select o from OrderEntity o join fetch o.instrument
			where o.userId = :userId and o.status in :statuses
			order by o.datetime asc, o.id asc
			""")
	List<OrderEntity> findOrdersOf(@Param("userId") Integer userId, @Param("statuses") Collection<String> statuses);

}
