package com.cocos.trading;

import org.springframework.boot.SpringApplication;

public class TestTradingApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(TradingApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
