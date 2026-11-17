package com.cocos.trading.controller.dto;

import java.math.BigDecimal;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public final class CashTransferRequest {

	private String side;

	private BigDecimal amount;

	private final Set<String> otherFields = new TreeSet<>();

	public String getSide() {
		return side;
	}

	public void setSide(String side) {
		this.side = side;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	@JsonAnySetter
	public void otherField(String name, Object value) {
		otherFields.add(name);
	}

	public Set<String> otherFields() {
		return Set.copyOf(otherFields);
	}

}
