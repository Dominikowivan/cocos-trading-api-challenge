package com.cocos.trading.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_idempotency")
public class OrderIdempotencyEntity {

	private static final int FINGERPRINT_LENGTH = 64;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Integer id;

	@Column(name = "userid")
	private Integer userId;

	@Column(name = "idempotency_key")
	private String idempotencyKey;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "request_fingerprint", length = FINGERPRINT_LENGTH)
	private String requestFingerprint;

	@Column(name = "order_id")
	private Integer orderId;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	protected OrderIdempotencyEntity() {
	}

	public void attach(Integer orderId) {
		this.orderId = orderId;
	}

	public Integer getId() {
		return id;
	}

	public Integer getUserId() {
		return userId;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getRequestFingerprint() {
		return requestFingerprint;
	}

	public Integer getOrderId() {
		return orderId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

}
