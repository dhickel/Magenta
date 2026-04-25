## Core Package

This package owns shared application services and cross-domain building blocks.

### Responsibilities
- Hold shared code only when more specific packages would otherwise duplicate real behavior.
- Keep core abstractions small and justified by current callers.
- Avoid turning this package into a catch-all.

### Change guidance
- Prefer domain packages over `core` for feature-specific behavior.
- Add shared abstractions only when they simplify existing code now.
- Keep names concrete and easy to understand.
- Keep this guide updated when shared responsibilities or core conventions change.

### Validation
- Add focused tests for reusable behavior introduced here.
- Run tests for all packages that call changed core APIs.
