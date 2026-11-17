package com.cocos.trading.persistence;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cocos.trading.persistence.entity.OrderIdempotencyEntity;

public interface OrderIdempotencyRepository extends JpaRepository<OrderIdempotencyEntity, Integer> {

	@Modifying
	@Query(value = """
			INSERT INTO order_idempotency (userid, idempotency_key, request_fingerprint, created_at)
			VALUES (:userId, :key, :fingerprint, :claimedAt)
			ON CONFLICT (userid, idempotency_key) DO NOTHING
			""", nativeQuery = true)
	int claim(@Param("userId") Integer userId, @Param("key") String key, @Param("fingerprint") String fingerprint,
			@Param("claimedAt") LocalDateTime claimedAt);

	Optional<OrderIdempotencyEntity> findByUserIdAndIdempotencyKey(Integer userId, String key);

	default Optional<String> fingerprintOf(Integer userId, String key) {
		return findByUserIdAndIdempotencyKey(userId, key).map(OrderIdempotencyEntity::getRequestFingerprint);
	}

	default Optional<Integer> orderIdOf(Integer userId, String key) {
		return findByUserIdAndIdempotencyKey(userId, key).map(OrderIdempotencyEntity::getOrderId);
	}

	default void attachOrderTo(Integer userId, String key, Integer orderId) {
		findByUserIdAndIdempotencyKey(userId, key)
			.orElseThrow(() -> new IllegalStateException(
					"the key this transaction claimed for user " + userId + " is no longer there"))
			.attach(orderId);
	}

}
