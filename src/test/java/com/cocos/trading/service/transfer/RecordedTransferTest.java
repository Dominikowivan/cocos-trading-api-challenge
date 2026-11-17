package com.cocos.trading.service.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.cocos.trading.model.Price;
import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.model.order.CashMovement;
import com.cocos.trading.model.order.LedgerSide;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.OrderKind;
import com.cocos.trading.model.order.OrderSide;
import com.cocos.trading.model.order.OrderStatus;
import com.cocos.trading.model.user.UserId;

class RecordedTransferTest {

	private static final InstrumentRef ARS = new InstrumentRef(66, "ARS", "PESOS", "MONEDA");

	private static final InstrumentRef PAMP = new InstrumentRef(47, "PAMP", "Pampa Holding S.A.", "ACCIONES");

	@Test
	void a_cash_order_is_reported_as_the_transfer_it_records() {
		RecordedTransfer transfer = RecordedTransfer.of(12, orderOf(ARS, CashMovement.CASH_IN));

		assertThat(transfer.orderId()).isEqualTo(12);
		assertThat(transfer.side()).isEqualTo(CashMovement.CASH_IN);
		assertThat(transfer.amount()).isEqualByComparingTo("100");
	}

	@Test
	void an_order_against_the_market_is_never_reported_as_a_transfer() {
		assertThatIllegalStateException().isThrownBy(() -> RecordedTransfer.of(7, orderOf(PAMP, OrderSide.BUY)))
			.withMessageContaining("order 7 is a BUY");
	}

	private static NewOrder orderOf(InstrumentRef instrument, LedgerSide side) {
		return new NewOrder(new UserId(1), instrument, side, OrderKind.MARKET, OrderStatus.FILLED,
				BigDecimal.valueOf(100), Price.of(BigDecimal.ONE), Optional.empty(),
				LocalDateTime.parse("2026-11-17T15:04:05"));
	}

}
