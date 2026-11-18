package com.cocos.trading.model.submission;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cocos.trading.model.Price;
import com.cocos.trading.model.ledger.AccountSnapshot;
import com.cocos.trading.model.order.AvailableBalance;
import com.cocos.trading.model.order.NewOrder;
import com.cocos.trading.model.order.RejectionReason;
import com.cocos.trading.model.user.UserId;

public final class PricedSubmission {

	private final Submission submission;

	private final Price price;

	PricedSubmission(Submission submission, Price price) {
		this.submission = submission;
		this.price = price;
	}

	public Price price() {
		return price;
	}

	public OrderDecision decide(AccountSnapshot snapshot) {
		if (isMoreThanIsAvailable(snapshot)) {
			return OrderDecision.rejected(submission.side().whatRanOut(), quantity().requestedSize(), price);
		}
		BigDecimal size = quantity().sizeAt(price);
		if (isBelowOneShare(size)) {
			return OrderDecision.rejected(RejectionReason.SIZE_BELOW_ONE_SHARE, size, price);
		}
		return OrderDecision.accepted(submission.type().acceptedStatus(), size, price);
	}

	public NewOrder placedBy(UserId userId, OrderDecision decision, LocalDateTime at) {
		return new NewOrder(userId, submission.instrument(), submission.side(), submission.type(), decision.status(),
				decision.size(), decision.price(), decision.rejectionReason(), at);
	}

	private boolean isMoreThanIsAvailable(AccountSnapshot snapshot) {
		return committableValueIn(snapshot).compareTo(quantity().valueAt(price)) < 0;
	}

	private BigDecimal committableValueIn(AccountSnapshot snapshot) {
		return submission.side().committableValue(availableIn(snapshot), price);
	}

	private AvailableBalance availableIn(AccountSnapshot snapshot) {
		long instrumentId = submission.instrument().instrumentId();
		return new AvailableBalance(snapshot.availableCash(), snapshot.availableQuantityOf(instrumentId));
	}

	private TradeQuantity quantity() {
		return submission.quantity();
	}

	private static boolean isBelowOneShare(BigDecimal size) {
		return size.signum() == 0;
	}

}
