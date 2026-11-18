package com.cocos.trading.observability;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.cocos.trading.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@Import(TestcontainersConfiguration.class)
class ActuatorEndpointsTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void health_reports_up_with_the_database_reachable() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void prometheus_exposes_the_requests_served() throws Exception {
		mvc.perform(get("/api/v1/users/{userId}/portfolio", 1)).andExpect(status().isOk());

		mvc.perform(get("/actuator/prometheus"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("http_server_requests_seconds_count")))
			.andExpect(content().string(containsString("uri=\"/api/v1/users/{userId}/portfolio\"")));
	}

	@ParameterizedTest
	@ValueSource(strings = { "env", "beans", "configprops", "metrics", "loggers", "heapdump", "threaddump" })
	void no_other_actuator_endpoint_is_exposed(String endpoint) throws Exception {
		mvc.perform(get("/actuator/{endpoint}", endpoint)).andExpect(status().isNotFound());
	}

}
