package com.cocos.trading.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "instruments")
public class InstrumentEntity {

	@Id
	@Column(name = "id")
	private Integer id;

	@Column(name = "ticker")
	private String ticker;

	@Column(name = "name")
	private String name;

	@Column(name = "type")
	private String type;

	protected InstrumentEntity() {
	}

	public Integer getId() {
		return id;
	}

	public String getTicker() {
		return ticker;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

}
