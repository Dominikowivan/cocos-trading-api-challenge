package com.cocos.trading.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity {

	@Id
	@Column(name = "id")
	private Integer id;

	protected UserEntity() {
	}

	public Integer getId() {
		return id;
	}

}
