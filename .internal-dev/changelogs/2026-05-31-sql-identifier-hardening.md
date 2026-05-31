# Date

2026-05-31

# Change Summary

Remediated GitHub issue #9 by hardening chat/audit/plan repository SQL identifier construction.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatSessionMetadataRepository.java`: restricted favorite/archive flag SQL columns to a private whitelist before identifier interpolation.
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/AuditRepository.java`: replaced suffix-derived audit migration types with explicit whitelisted column/type pairs.
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanRepository.java`: restricted warm `plan_runs` migration DDL to known table/column/statement triples.
- Focused repository tests under `src/test/java/io/mindspice/magenta2/ai/chat/repository/` and `src/test/java/io/mindspice/magenta2/ai/chat/plan/`: covered malicious identifier payload rejection plus normal flag and warm-schema behavior.
- `.internal-dev/specifications/services.md`, `docs/technical/architecture.md`, `docs/technical/data-model.md`, and `docs/technical/security.md`: documented the repository-owned SQL identifier/DDL whitelist contract.

# Behavioral Impact

Normal chat session favorite/archive behavior and repository schema bootstrapping remain unchanged for supported columns. Unsupported SQL identifier or DDL payloads now fail before reaching SQLite.

# Specification Impact

Added `SVC-20260531-01` to record the repository schema bootstrapping safety contract.

# Validation

- `mvn -q -Dtest=ChatSessionMetadataRepositoryTest,AuditRepositoryTest,PlanRepositoryTest test`

# Risks

Validation was focused to the Phase 01 repository scope. No Spring wiring changed, and no startup smoke was required by the directive for this phase.
