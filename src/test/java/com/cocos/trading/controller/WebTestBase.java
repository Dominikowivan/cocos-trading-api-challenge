package com.cocos.trading.controller;

import static com.cocos.trading.controller.ApiPaths.INSTRUMENT_SEARCH;
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
public abstract class WebTestBase {

	@Autowired
	protected MockMvc mvc;

	protected ResultActions search(String query) throws Exception {
		return mvc.perform(get(INSTRUMENT_SEARCH).param("query", query));
	}

	protected static String bodyOf(ResultActions response) throws Exception {
		return response.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

}
