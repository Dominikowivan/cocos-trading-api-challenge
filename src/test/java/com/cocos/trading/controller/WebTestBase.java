package com.cocos.trading.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.cocos.trading.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ContractValidationConfiguration.class })
abstract class WebTestBase {

	protected static final String PORTFOLIO = "/api/v1/users/{userId}/portfolio";

	@Autowired
	protected MockMvc mvc;

}
