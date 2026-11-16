package com.cocos.trading.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class OrderEntity {

	@Id
	@Column(name = "id")
	private Integer id;

	@Column(name = "userid")
	private Integer userId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "instrumentid")
	private InstrumentEntity instrument;

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
