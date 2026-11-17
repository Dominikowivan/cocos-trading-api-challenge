package com.cocos.trading.model.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.cocos.trading.model.order.CashMovement;

class CashTransferAmountTest {

	@Test
	void one_hundred_written_with_or_without_decimals_is_one_hundred_pesos() {
		assertThat(depositOf("100").amount()).isEqualTo(new BigDecimal("100"));
		assertThat(depositOf("100.00").amount()).isEqualTo(new BigDecimal("100"));
	}

	@Test
	void one_peso_is_the_smallest_transfer_there_is() {
		assertThat(depositOf("1").amount()).isEqualByComparingTo("1");
	}

	@Test
	void the_largest_transfer_is_what_the_ledgers_size_column_holds() {
		assertThat(depositOf("2147483647").amount()).isEqualByComparingTo("2147483647");
	}

	@Test
	void centavos_are_refused_rather_than_rounded() {
		assertThatIllegalArgumentException().isThrownBy(() -> depositOf("100.50"))
			.withMessageContaining("whole number of pesos")
			.withMessageContaining("100.50");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-5", "2147483648" })
	void an_amount_outside_the_ledgers_bounds_is_refused_naming_it(String amount) {
		assertThatIllegalArgumentException().isThrownBy(() -> depositOf(amount))
			.withMessageContaining("between 1 and 2147483647")
			.withMessageContaining(amount);
	}

	@Test
	void a_missing_amount_is_refused() {
		assertThatIllegalArgumentException().isThrownBy(() -> CashTransfer.of(CashMovement.CASH_IN, null))
			.withMessageContaining("'amount'");
	}

	@Test
	void a_withdrawal_follows_the_same_rules() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> CashTransfer.of(CashMovement.CASH_OUT, new BigDecimal("0.99")));
	}

	private static CashTransfer depositOf(String amount) {
		return CashTransfer.of(CashMovement.CASH_IN, new BigDecimal(amount));
	}

}
