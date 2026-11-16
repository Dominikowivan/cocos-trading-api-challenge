package com.cocos.trading.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cocos.trading.controller.dto.PortfolioResponse;
import com.cocos.trading.model.user.UserId;
import com.cocos.trading.service.portfolio.PortfolioService;

@RestController
@RequestMapping("/api/v1/users/{userId}/portfolio")
class PortfolioController {

	private final PortfolioService portfolioService;

	PortfolioController(PortfolioService portfolioService) {
		this.portfolioService = portfolioService;
	}

	@GetMapping
	PortfolioResponse portfolioOf(@PathVariable long userId) {
		UserId requested = new UserId(userId);
		return PortfolioResponse.of(requested, portfolioService.portfolioOf(requested));
	}

}
