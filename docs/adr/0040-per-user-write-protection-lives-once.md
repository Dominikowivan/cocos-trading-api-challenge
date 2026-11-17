# ADR-0040: The per-user write protection lives once, in `service/userwrite`

- **Status:** Accepted
- **Date:** 2026-11-17
- **Journal:** [08CashTransfer](../journal/08CashTransfer.md)

## Context

Order submission protects every write with three steps in a fixed order: verify the user exists,
claim the idempotency key when one was sent (ADR-0037), then lock the user's row (ADR-0036) — and,
after the work, attach the order to the key, or, when the claim was lost, replay the recorded outcome
or answer `409`. Cash transfer (ADR-0038) needs exactly the same, and cancellation will be the third.

The journal sequences it: build the transfer's protections following submission's pattern, then
"ABSTRAEMOS la lógica duplicada de claim de idempotencia y lockeo de usuario … va a ser mucho más
limpio que viva en su propia abstracción". Constitution 2.3.1 left it nowhere to go: use cases may
not depend on each other, and nothing may sit at the root of `service`.

## Decision

**After both endpoints have their protections and their tests, the protection is extracted into
`service/userwrite`: Spring `@Component`s, not `@Service`s, that both use cases call and that depend
on neither.** The two protections are orthogonal, so each is its own component, and a third one
composes them in the order the write endpoints need:

1. **`UserLock`** — the lock on the user's row (ADR-0036): takes it, then runs the work. It knows
   nothing of keys.
2. **`IdempotentWrite`** — the claim of the key (ADR-0037) and its three outcomes: attach the order
   the work appended, replay the recorded outcome when the fingerprints agree, `409` when they do
   not. It knows nothing of the lock. Its keys are still scoped per user, because
   `order_idempotency` is unique on `(userid, idempotency_key)`.
3. **`UserWrite`** — what submission and transfer call: the user's existence (non-locking read →
   `404`) before any claim, then the claim, then the lock, then the work. The order between the
   claim and the lock lives here and only here, so a replay or a `409` never waits on the lock.

The package also owns the key's type, the conflict exception and the hashing and comparison of a
canonical form. Each use case keeps what is its own: the canonical form of its request, the work
(read, decide, append) and how a recorded order becomes its response. Each component runs **only
inside the caller's transaction** and refuses to open one: a lock outside one is released at once,
and a claim outside one would not roll back with a refused request (ADR-0037's consequence).

Constitution 2.4.0 admits exactly this package in principle VIII. `ArchitectureTest` enforces it:
use cases may depend on `service/userwrite` and on no other use case, `service/userwrite` depends on
no use case, only `UserLock` calls `UserRepository.lockById`, only `IdempotentWrite` calls
`OrderIdempotencyRepository.claim`, and neither of the two depends on the other.

## Rationale

The lock and the claim are orchestration — statements of one transaction in an order that matters —
and principle VIII keeps orchestration in the service layer. A named subpackage that is not a use case
is the only place in that layer the rule of independent use cases leaves. Making "only this package
locks and claims" an ArchUnit rule turns "implemented once" from a claim into a check.

Extracting after both endpoints, not before, is the journal's order and the safer one: the
abstraction is shaped by two real callers, and both suites, written against the duplicated code,
prove the extraction changed nothing.

## Alternatives considered

- **Persistence.** The lock and the claim are already repository methods, and no amendment would be
  needed. Rejected: the claim → work → attach / replay → conflict flow is orchestration, which
  persistence does not do.
- **A top-level package outside the four layers.** Rejected: it would fall outside every layer rule
  unless a fifth layer were added, a larger change for one class.
- **An AOP annotation** (`@SerialisedPerUser`). Rejected: invisible at the call site, and broken by
  self-invocation.
- **One component owning existence, claim and lock together.** The first shape of the extraction.
  Rejected: the lock and the claim are orthogonal — an endpoint may want a retry-safe key without
  serialising on the user, or the lock without a key — and one component would make either
  impossible without the other. Splitting keeps each usable alone while one composition still fixes
  their order.
- **Leaving the copy in each use case.** Rejected: constitution IX, a rule in one place; and the third
  copy is already coming with cancellation.
- **Extracting first and building the transfer's protections on it.** Rejected: contradicts the
  author's sequence and designs the abstraction from one caller.

## Consequences

- Between the transfer's idempotency story and the extraction, the claim flow exists twice; the
  extraction is part of the same feature.
- Submission's behaviour, its tests and its fingerprint's canonical form do not change.
- Cancellation gets the existence check, the lock and the idempotency claim by calling `UserWrite`;
  an endpoint that needs only one of the two protections calls `UserLock` or `IdempotentWrite`
  directly.
- The package may hold only these protections; anything else two use cases share is not a reason to
  add to it (constitution VIII).
