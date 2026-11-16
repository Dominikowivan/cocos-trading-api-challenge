package com.cocos.trading.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.cocos.trading.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ContractValidationConfiguration.class })
abstract class WebTestBase {

	protected static final String PORTFOLIO = "/api/v1/users/{userId}/portfolio";

	protected static final String INSTRUMENT_SEARCH = "/api/v1/instruments/search";

	@Autowired
	protected MockMvc mvc;

	protected ResultActions search(String query) throws Exception {
		return mvc.perform(get(INSTRUMENT_SEARCH).param("query", query));
	}

	protected static String bodyOf(ResultActions response) throws Exception {
		return response.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

}
