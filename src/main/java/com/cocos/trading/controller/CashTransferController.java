package com.cocos.trading.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cocos.trading.controller.dto.request.CashTransferRequest;
import com.cocos.trading.controller.dto.response.CashTransferResponse;
import com.cocos.trading.service.transfer.CashTransferService;
import com.cocos.trading.service.transfer.TransferRequest;

@RestController
@RequestMapping("/api/v1/users/{userId}/cash-transfers")
class CashTransferController {

	private final CashTransferService cashTransferService;

	CashTransferController(CashTransferService cashTransferService) {
		this.cashTransferService = cashTransferService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CashTransferResponse transfer(@PathVariable long userId, @RequestBody CashTransferRequest body,
			@RequestHeader(name = IdempotencyHeader.NAME, required = false) String idempotencyKey) {
		return CashTransferResponse.from(cashTransferService.transfer(transferOf(userId, body, idempotencyKey)));
	}

	private static TransferRequest transferOf(long userId, CashTransferRequest body, String idempotencyKey) {
		return new TransferRequest(userId, body.getSide(), body.getAmount(), body.otherFields(), idempotencyKey);
	}

}
