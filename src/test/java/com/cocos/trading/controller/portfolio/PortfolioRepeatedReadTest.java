package com.cocos.trading.controller.portfolio;

import static com.cocos.trading.controller.ApiPaths.PORTFOLIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.cocos.trading.controller.WebTestBase;

class PortfolioRepeatedReadTest extends WebTestBase {

	@Test
	void two_reads_with_no_intervening_order_return_the_same_bytes() throws Exception {
		String first = read(1);
		String second = read(1);

		assertThat(second).isEqualTo(first);
	}

	@Test
	void the_position_order_is_part_of_what_repeats() throws Exception {
		String body = read(1);

		assertThat(body.indexOf("BMA")).isLessThan(body.indexOf("METR"));
		assertThat(body.indexOf("METR")).isLessThan(body.indexOf("PAMP"));
	}

	private String read(long userId) throws Exception {
		return mvc.perform(get(PORTFOLIO, userId))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

}
