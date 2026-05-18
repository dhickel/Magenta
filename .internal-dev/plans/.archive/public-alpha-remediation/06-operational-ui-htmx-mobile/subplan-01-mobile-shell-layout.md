# Subplan 01: Mobile Shell Layout

## Goal

Make orchestration shell usable at phone width.

## Implementation Steps

1. Inspect SimplyPages sidebar CSS and Magenta overrides.
2. Fix specificity so mobile layout becomes content-first or overlay sidebar.
3. Preserve desktop layout.
4. Add Playwright viewport checks for measured content width and no incoherent overlap.

## Validation

At `390x780`, content area is usable and no longer 70-100px wide.
