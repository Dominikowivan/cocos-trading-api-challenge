package com.cocos.trading.service.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.cocos.trading.TestcontainersConfiguration;

class SearchPropertiesTest {

	@Nested
	@SpringBootTest
	@Import(TestcontainersConfiguration.class)
	class WithNothingOverridden {

		@Autowired
		private SearchProperties properties;

		@Test
		void the_shipped_defaults_bind() {
			assertThat(properties.defaultPageSize()).isEqualTo(20);
			assertThat(properties.maxPageSize()).isEqualTo(100);
			assertThat(properties.maxQueryLength()).isEqualTo(64);
		}

		@Test
		void the_limits_the_model_validates_against_carry_those_values() {
			assertThat(properties.limits()).isEqualTo(new SearchLimits(64, 20, 100));
		}

	}

	@Nested
	@SpringBootTest(properties = { "trading.search.default-page-size=5", "trading.search.max-page-size=50",
			"trading.search.max-query-length=10" })
	@Import(TestcontainersConfiguration.class)
	class WithADeploymentOverride {

		@Autowired
		private SearchProperties properties;

		@Test
		void configuration_produces_different_limits_with_no_rebuild() {
			assertThat(properties.limits()).isEqualTo(new SearchLimits(10, 5, 50));
		}

	}

}
