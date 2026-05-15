# Date
2026-05-13

# Change Summary
- Aligned container setup script defaults with orchestration runtime expectations by removing `slim` image defaults.
- Updated setup script examples and help text to use non-slim tags.

# Files
- `.internal-dev/scripts/docker-setup.sh`

# Behavioral Impact
- Running `./.internal-dev/scripts/docker-setup.sh` now pulls and validates `python:3.11` by default instead of `python:3.11-slim`.
- Prevents future setup drift between script defaults and runtime configuration/docs.

# Risks
- Hosts with previously cached `*-slim` images will now pull the non-slim image on first run.

# Follow-up Items
- Re-run the setup script (or verify-only with explicit image) to confirm host now has the non-slim image.
