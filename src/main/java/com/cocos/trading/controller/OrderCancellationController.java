package com.cocos.trading.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cocos.trading.controller.dto.response.OrderResponse;
import com.cocos.trading.service.cancellation.CancellationRequest;
import com.cocos.trading.service.cancellation.OrderCancellationService;

@RestController
@RequestMapping("/api/v1/users/{userId}/orders")
class OrderCancellationController {

	private final OrderCancellationService orderCancellationService;

	OrderCancellationController(OrderCancellationService orderCancellationService) {
		this.orderCancellationService = orderCancellationService;
	}

	@PostMapping("/{orderId}/cancel")
	OrderResponse cancel(@PathVariable long userId, @PathVariable long orderId,
			@RequestHeader(name = IdempotencyHeader.NAME, required = false) String idempotencyKey) {
		return OrderResponse
			.of(orderCancellationService.cancel(new CancellationRequest(userId, orderId, idempotencyKey)));
	}

}
