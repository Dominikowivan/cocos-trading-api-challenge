package com.cocos.trading.service.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.cocos.trading.TestcontainersConfiguration;

class SearchPropertiesTest {

	@Test
	void a_maximum_query_length_below_one_cannot_be_built() {
		assertThatThrownBy(() -> new SearchLimits(0)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("The maximum query length must be at least 1, but was 0");
	}

	@Nested
	@SpringBootTest
	@Import(TestcontainersConfiguration.class)
	class WithNothingOverridden {

		@Autowired
		private SearchProperties properties;

		@Test
		void the_shipped_default_binds() {
			assertThat(properties.limits()).isEqualTo(new SearchLimits(64));
		}

	}

	@Nested
	@SpringBootTest(properties = "trading.search.max-query-length=10")
	@Import(TestcontainersConfiguration.class)
	class WithADeploymentOverride {

		@Autowired
		private SearchProperties properties;

		@Test
		void configuration_produces_different_limits_with_no_rebuild() {
			assertThat(properties.limits()).isEqualTo(new SearchLimits(10));
		}

	}

}
