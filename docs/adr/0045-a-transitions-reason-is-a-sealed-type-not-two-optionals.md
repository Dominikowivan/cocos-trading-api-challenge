# ADR-0045: A transition's reason is a sealed type, not two `Optional`s

- **Status:** Accepted
- **Date:** 2026-11-17
- **Journal:** [10Refactors](../journal/10Refactors.md)
- **Refines:** [ADR-0042](0042-the-history-reason-says-why-the-order-moved.md)

## Context

[ADR-0042](0042-the-history-reason-says-why-the-order-moved.md) made one `reason` column carry both
kinds of reason: why an order was rejected, and why it was cancelled. Reading a row therefore has to
decide which kind it is holding, and the invariant that matters is that it never confuses them — a
`CANCELLED` row's `USER_REQUESTED` must not be read as a rejection reason.

`RecordedTransition` expressed that with two `Optional` fields, `rejectionReason` and
`cancellationReason`, of which at most one is ever present. Nothing in the type said "at most one":
a transition with both, or a `REJECTED` one carrying a cancellation reason, was constructible and
would have compiled.

Two further facts, measured in the code rather than assumed:

- **No production code reads `cancellationReason`.** The mapper filled it on every history read and
  only tests looked at it.
- Those tests are not incidental. `OrderStatusHistoryReasonTest.a_cancelled_row_is_read_as_a_cancellation_and_never_as_a_rejection`
  is the test of ADR-0042's invariant, and the second `Optional` was how it said "the reason landed
  in the cancellation slot" rather than merely "the rejection slot is empty".

## Decision

**The reason of a recorded transition is one value of a sealed type:**

```java
public sealed interface TransitionReason {

    Optional<RejectionReason> rejection();

    record Rejected(RejectionReason reason) implements TransitionReason { … }

    record Cancelled(CancellationReason reason) implements TransitionReason { … }

    record Unstated() implements TransitionReason { … }

}
```

`RecordedTransition` becomes `(OrderStatus status, TransitionReason reason)`. `Unstated` is the
reason of a `NEW` or `FILLED` transition, which records none — an absent reason is a case of the
type, not a `null` and not an empty `Optional`.

The one question production code asks of a transition — *was this a rejection, and why* — is
answered by `rejection()`, implemented per case rather than by a `switch` over the cases.

## Rationale

Mutually exclusive states belong in the type, which is how the model already answers this
question three times: `TradeQuantity` is sealed because a submission states either a size or an
amount and never both, `CancellationDecision` because a cancellation either happened or the order
was not cancellable, and `LedgerSide` because a row moves either shares or cash. None of those
three needed an ADR — they were the obvious shape at the time. This one gets one because the shape
it replaces had shipped, and because the schema it reads is deliberately overloaded, which is the
part worth explaining.

Two `Optional`s describe four combinations to express three, and the fourth is unrepresentable
nonsense that the compiler cannot refuse. A sealed type has exactly the cases that exist.

It also keeps ADR-0042's invariant stated instead of implied. The test now reads
`new RecordedTransition(CANCELLED, new TransitionReason.Cancelled(USER_REQUESTED))` — a cancellation
with its reason — rather than an empty slot beside a full one. A reader of the test learns the rule;
a reader of the previous one learned a shape.

`rejection()` is implemented on each case instead of being a `switch` in the mapper or the service,
so adding a fourth kind of reason is a new record that the compiler forces to answer the question,
not a `default` branch that silently answers it wrong — the same construction `OrderType` uses to
map a type to its accepted status.

## Alternatives considered

- **Keep the two `Optional`s.** No work, and it was passing every test. It leaves a type whose legal
  values include two that cannot exist, in the one place the schema deliberately overloaded a column.
- **Delete `cancellationReason` and keep a single `Optional<RejectionReason>`.** The smallest change,
  and it was the first conclusion from finding that nothing reads it. Rejected on reading the test it
  would have weakened: the assertion would have shrunk to "a cancelled row carries no rejection
  reason", which is half of ADR-0042's invariant. Removing the only statement of a rule because the
  application does not currently need it is how the rule stops being true later.
- **Move `RecordedTransition` out of the model, into `service`.** The author's first reading was that
  it felt like a service object, because it holds no behaviour. Rejected: it is what the persistence
  mapper is required to hand out instead of an entity
  ([ADR-0014](0014-data-access-with-spring-data-jpa.md)), so it has to be a model type or each use
  case would map the history itself. With `rejection()` on the reason, the behaviour it was missing
  is where it belongs.

## Consequences

- No behaviour changes: every HTTP response, every stored row and every status is as before. The
  change is which values the code can express.
- The history read no longer computes a cancellation reason that nothing consumed.
- A reason that is absent and a reason that is a cancellation are now different cases, so code that
  handles reasons cannot treat "no reason" as "not a rejection" by accident.
