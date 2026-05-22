# Context
Run temp cleanup and output validation need clearer retention behavior.

# Goal
Add `retainTempWork` and document run retention behavior.

# In Scope
- Runtime settings schema/UI/API updates.
- Temp cleanup retention for review states.
- Docs and changelog updates.

# Out of Scope
- Broad artifact inference.

# Implementation Steps
- Add the persisted setting and service accessor.
- Clean temp only on successful clean completion when retention is disabled.
- Update relevant docs and changelog.

# Validation
- Runtime settings tests and Spring context smoke.

# Exit Criteria
- Operators can choose whether successful runs remove temp work automatically.
