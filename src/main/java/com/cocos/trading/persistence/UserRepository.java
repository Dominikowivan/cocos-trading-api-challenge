package com.cocos.trading.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cocos.trading.persistence.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {

}
