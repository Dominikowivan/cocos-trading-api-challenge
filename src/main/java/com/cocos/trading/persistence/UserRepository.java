package com.cocos.trading.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cocos.trading.persistence.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from UserEntity u where u.id = :id")
	Optional<UserEntity> lockById(@Param("id") Integer id);

}
