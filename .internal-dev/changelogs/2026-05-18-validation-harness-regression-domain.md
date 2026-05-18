# Validation Harness Regression Domain

## Summary

- Completed the public alpha validation harness regression domain.
- Added durable Spring route/context coverage, a live-app Playwright harness, SQLite fixture parity checks, schedule/reaction parity tests, and targeted regression-gap tests for previously missed blocker classes.
- Validated the domain with focused Maven tests, full `mvn test`, SQLite foreign-key fixture scans, live Playwright against Spring Boot, and clean/warm SQLite startup.

## Validation

- Focused Maven: `/tmp/domain07-focused-maven.log`
- Full Maven: `/tmp/domain07-full-mvn.log`
- Live Spring: `/tmp/domain07-live-spring.log`
- Playwright harness: `/tmp/domain07-playwright-public-alpha.log`
- Clean startup: `/tmp/domain07-clean-startup.log`
- Warm startup: `/tmp/domain07-warm-startup.log`
