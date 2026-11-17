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
`service/userwrite`: one Spring `@Component`, not a `@Service`, that both use cases call and that
depends on neither.**

It owns, once:

1. the user's existence (non-locking read → `404`), before any claim;
2. the claim of the key, and its three outcomes: attach the order the work appended, replay the
   recorded outcome when the fingerprints agree, `409` when they do not;
3. the lock on the user's row, after the claim and before the work;
4. the key's type, the conflict exception and the hashing and comparison of a canonical form.

Each use case keeps what is its own: the canonical form of its request, the work (read, decide,
append) and how a recorded order becomes its response. The component runs **only inside the
caller's transaction** and refuses to open one, so a refused request still rolls its claim back
(ADR-0037's consequence).

Constitution 2.4.0 admits exactly this package in principle VIII. `ArchitectureTest` enforces it:
use cases may depend on `service/userwrite` and on no other use case, `service/userwrite` depends on
no use case, and only `service/userwrite` calls `UserRepository.lockById` and
`OrderIdempotencyRepository.claim`.

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
- **Leaving the copy in each use case.** Rejected: constitution IX, a rule in one place; and the third
  copy is already coming with cancellation.
- **Extracting first and building the transfer's protections on it.** Rejected: contradicts the
  author's sequence and designs the abstraction from one caller.

## Consequences

- Between the transfer's idempotency story and the extraction, the claim flow exists twice; the
  extraction is part of the same feature.
- Submission's behaviour, its tests and its fingerprint's canonical form do not change.
- Cancellation gets the existence check, the lock and the idempotency claim by calling one component.
- The package may hold only these protections; anything else two use cases share is not a reason to
  add to it (constitution VIII).
