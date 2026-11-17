package com.cocos.trading.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class OrderEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Integer id;

	@Column(name = "userid")
	private Integer userId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "instrumentid", insertable = false, updatable = false)
	private InstrumentEntity instrument;

	@Column(name = "instrumentid")
	private Integer instrumentId;

	@Column(name = "size")
	private int size;

	@Column(name = "price")
	private BigDecimal price;

	@Column(name = "side")
	private String side;

	@Column(name = "status")
	private String status;

	@Column(name = "type")
	private String type;

	@Column(name = "datetime")
	private LocalDateTime datetime;

	protected OrderEntity() {
	}

	public static OrderEntity newOrder(Integer userId, Integer instrumentId, int size, BigDecimal price, String side,
			String status, String type, LocalDateTime datetime) {
		OrderEntity order = new OrderEntity();
		order.userId = userId;
		order.instrumentId = instrumentId;
		order.size = size;
		order.price = price;
		order.side = side;
		order.status = status;
		order.type = type;
		order.datetime = datetime;
		return order;
	}

	public Integer getId() {
		return id;
	}

	public Integer getUserId() {
		return userId;
	}

	public InstrumentEntity getInstrument() {
		return instrument;
	}

	public int getSize() {
		return size;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public String getSide() {
		return side;
	}

	public String getStatus() {
		return status;
	}

	public String getType() {
		return type;
	}

	public LocalDateTime getDatetime() {
		return datetime;
	}

}
