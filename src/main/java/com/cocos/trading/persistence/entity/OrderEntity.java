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

	public static Builder newOrder() {
		return new Builder();
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

	public static final class Builder {

		private final OrderEntity order = new OrderEntity();

		private Builder() {
		}

		public Builder ofUser(Integer userId) {
			order.userId = userId;
			return this;
		}

		public Builder inInstrument(Integer instrumentId) {
			order.instrumentId = instrumentId;
			return this;
		}

		public Builder ofSize(int size) {
			order.size = size;
			return this;
		}

		public Builder atPrice(BigDecimal price) {
			order.price = price;
			return this;
		}

		public Builder onSide(String side) {
			order.side = side;
			return this;
		}

		public Builder withStatus(String status) {
			order.status = status;
			return this;
		}

		public Builder ofType(String type) {
			order.type = type;
			return this;
		}

		public OrderEntity placedAt(LocalDateTime datetime) {
			order.datetime = datetime;
			return order;
		}

	}

}
