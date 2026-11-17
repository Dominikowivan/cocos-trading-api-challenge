package com.cocos.trading.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cocos.trading.controller.dto.request.OrderSubmissionRequest;
import com.cocos.trading.controller.dto.response.OrderResponse;
import com.cocos.trading.service.submission.OrderSubmissionService;
import com.cocos.trading.service.submission.SubmissionRequest;

@RestController
@RequestMapping("/api/v1/users/{userId}/orders")
class OrderSubmissionController {

	private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	private final OrderSubmissionService orderSubmissionService;

	OrderSubmissionController(OrderSubmissionService orderSubmissionService) {
		this.orderSubmissionService = orderSubmissionService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	OrderResponse submit(@PathVariable long userId, @RequestBody OrderSubmissionRequest body,
			@RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
		return OrderResponse.of(orderSubmissionService.submit(submissionOf(userId, body, idempotencyKey)));
	}

	private static SubmissionRequest submissionOf(long userId, OrderSubmissionRequest body, String idempotencyKey) {
		return new SubmissionRequest(userId, body.instrumentId(), body.type(), body.side(), body.size(), body.amount(),
				body.price(), idempotencyKey);
	}

}
