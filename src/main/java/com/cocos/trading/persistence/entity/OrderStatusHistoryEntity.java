package com.cocos.trading.persistence.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistoryEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Integer id;

	@Column(name = "order_id")
	private Integer orderId;

	@Column(name = "status")
	private String status;

	@Column(name = "reason")
	private String reason;

	@Column(name = "changed_at")
	private LocalDateTime changedAt;

	protected OrderStatusHistoryEntity() {
	}

	public static OrderStatusHistoryEntity newTransition(Integer orderId, String status, String reason,
			LocalDateTime changedAt) {
		OrderStatusHistoryEntity transition = new OrderStatusHistoryEntity();
		transition.orderId = orderId;
		transition.status = status;
		transition.reason = reason;
		transition.changedAt = changedAt;
		return transition;
	}

	public Integer getId() {
		return id;
	}

	public Integer getOrderId() {
		return orderId;
	}

	public String getStatus() {
		return status;
	}

	public String getReason() {
		return reason;
	}

	public LocalDateTime getChangedAt() {
		return changedAt;
	}

}
