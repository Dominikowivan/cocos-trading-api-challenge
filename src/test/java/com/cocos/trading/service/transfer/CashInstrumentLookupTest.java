package com.cocos.trading.service.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.cocos.trading.TestcontainersConfiguration;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CashInstrumentLookupTest {

	private static final int A_SECOND_CURRENCY = 9001;

	@Autowired
	private CashTransferService cashTransferService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void aSecondCashInstrument() {
		jdbcTemplate.update("INSERT INTO instruments (id, ticker, name, type) VALUES (?, 'USD', 'DOLAR', 'MONEDA')",
				A_SECOND_CURRENCY);
	}

	@AfterEach
	void restoreTheSeed() {
		jdbcTemplate.update("DELETE FROM instruments WHERE id = ?", A_SECOND_CURRENCY);
	}

	@Test
	void with_more_than_one_cash_instrument_a_transfer_is_the_deployments_fault_and_writes_nothing() {
		long before = orders();

		assertThatIllegalStateException()
			.isThrownBy(() -> cashTransferService.transfer(new TransferRequest(2, "CASH_IN", BigDecimal.TEN)))
			.withMessageContaining("holds 2");

		assertThat(orders()).isEqualTo(before);
	}

	private long orders() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Long.class);
	}

}
