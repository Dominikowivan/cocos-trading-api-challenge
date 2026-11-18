package com.cocos.trading.model.submission;

import java.math.BigDecimal;

import com.cocos.trading.model.DecimalPolicy;
import com.cocos.trading.model.Price;

public abstract sealed class TradeQuantity {

	private static final BigDecimal SMALLEST_SIZE = BigDecimal.ONE;

	private static final BigDecimal LARGEST_SIZE = BigDecimal.valueOf(Integer.MAX_VALUE);

	private TradeQuantity() {
	}

	public static TradeQuantity of(BigDecimal size, BigDecimal amount) {
		requireEitherASizeOrAnAmount(size, amount);
		return size != null ? ofSize(size) : ofAmount(amount);
	}

	public static TradeQuantity ofSize(BigDecimal size) {
		BigDecimal shares = size.stripTrailingZeros();
		requireWholeShares(shares);
		requireWithinTheLedgersBounds(shares);
		return new ExactSize(shares);
	}

	public static TradeQuantity ofAmount(BigDecimal amount) {
		BigDecimal pesos = DecimalPolicy.money(amount);
		requireAnAmountWithinTheLedgersBounds(pesos);
		return new TotalAmount(pesos);
	}

	public abstract BigDecimal valueAt(Price price);

	public abstract BigDecimal sizeAt(Price price);

	public abstract BigDecimal requestedSize();

	private static void requireEitherASizeOrAnAmount(BigDecimal size, BigDecimal amount) {
		if ((size != null) == (amount != null)) {
			throw new IllegalArgumentException(
					"An order states either a size in whole shares or a total amount in pesos, never both and never neither");
		}
	}

	private static void requireWholeShares(BigDecimal shares) {
		if (shares.scale() > 0) {
			throw new IllegalArgumentException("A size is a whole number of shares, and " + shares.toPlainString()
					+ " is not; send a total amount instead to trade by value");
		}
	}

	private static void requireWithinTheLedgersBounds(BigDecimal shares) {
		if (shares.compareTo(SMALLEST_SIZE) < 0 || shares.compareTo(LARGEST_SIZE) > 0) {
			throw new IllegalArgumentException("A size is between " + SMALLEST_SIZE.toPlainString() + " and "
					+ LARGEST_SIZE.toPlainString() + " shares, and " + shares.toPlainString() + " is not");
		}
	}

	private static void requireAnAmountWithinTheLedgersBounds(BigDecimal pesos) {
		if (!DecimalPolicy.fitsTheMoneyColumn(pesos)) {
			throw new IllegalArgumentException("A total amount is " + DecimalPolicy.moneyBounds() + " pesos, and "
					+ pesos.toPlainString() + " is not");
		}
	}

	private static final class ExactSize extends TradeQuantity {

		private final BigDecimal size;

		private ExactSize(BigDecimal size) {
			this.size = size;
		}

		@Override
		public BigDecimal valueAt(Price price) {
			return price.times(size);
		}

		@Override
		public BigDecimal sizeAt(Price price) {
			return size;
		}

		@Override
		public BigDecimal requestedSize() {
			return size;
		}

	}

	private static final class TotalAmount extends TradeQuantity {

		private final BigDecimal pesos;

		private TotalAmount(BigDecimal pesos) {
			this.pesos = pesos;
		}

		@Override
		public BigDecimal valueAt(Price price) {
			return pesos;
		}

		@Override
		public BigDecimal sizeAt(Price price) {
			return derived(price.wholeSharesIn(pesos), price);
		}

		@Override
		public BigDecimal requestedSize() {
			return BigDecimal.ZERO;
		}

		private BigDecimal derived(BigDecimal shares, Price price) {
			if (shares.compareTo(LARGEST_SIZE) > 0) {
				throw new IllegalArgumentException(
						pesos.toPlainString() + " at " + price + " is " + shares.toPlainString()
								+ " shares, more than the " + LARGEST_SIZE.toPlainString() + " an order can carry");
			}
			return shares;
		}

	}

}
