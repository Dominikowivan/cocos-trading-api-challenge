package com.cocos.trading.persistence.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "marketdata")
public class MarketDataEntity {

	@Id
	@Column(name = "id")
	private Integer id;

	@Column(name = "instrumentid")
	private Integer instrumentId;

	@Column(name = "close")
	private BigDecimal close;

	@Column(name = "date")
	private LocalDate date;

	protected MarketDataEntity() {
	}

	public Integer getId() {
		return id;
	}

	public Integer getInstrumentId() {
		return instrumentId;
	}

	public BigDecimal getClose() {
		return close;
	}

	public LocalDate getDate() {
		return date;
	}

}
