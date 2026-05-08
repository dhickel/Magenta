# Comprehensive Review Deferred Work

## Context
The comprehensive review remediation pass intentionally stayed focused on the remaining live runtime risks: non-blocking task SSE and orchestration lease heartbeats.

## Deferred Ideas
- Add formal schema migration tooling such as Flyway or Liquibase so orchestration table changes stop relying on inline `ensureSchema` compatibility checks.
- Split larger repository/service classes only when a concrete follow-up needs a smaller ownership boundary.
- Introduce runner polymorphism only after assignment execution paths diverge enough to justify separate strategy classes.
- Move model prompt text into explicit prompt templates when the current inline prompts become hard to test or reuse.
- Centralize orchestration JSON helpers after another repository needs the same map serialization behavior.
- Expand workflows from ordered steps to DAG execution only after a user-facing workflow needs branching, joins, or parallelism.
- Add distributed fencing tokens if Magenta moves beyond the current single-node SQLite operating target.

## Out of Scope Rationale
These items are architectural hardening or future capability work. They are not required to fix the current blocking review risks and would expand the patch beyond the requested scope.

## Status
Deferred.
