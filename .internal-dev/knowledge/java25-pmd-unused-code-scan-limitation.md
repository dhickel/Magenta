## Topic
PMD unused-code scanning limitation on Java 25 classfiles

## Source References
- `mvn -q org.apache.maven.plugins:maven-pmd-plugin:3.26.0:pmd ...`
- Build-time error: `Unsupported class file major version 69`

## Key Takeaways
- PMD run failed during Java symbol resolution because dependency classfiles in this environment are version 69.
- For this repo/runtime, PMD cannot be relied on for dead-code discovery without updating tooling to a Java 25-compatible analyzer stack.
- A conservative fallback is symbol-usage analysis for private members plus compile/test/startup validation.

## Engine Relevance
When asked for dead-code cleanup in this repo, prefer Java-25-compatible analyzers first; otherwise use conservative private-member usage checks and verify with full tests/startup.

## Open Questions
- Which analyzer/toolchain version should be standardized for Java 25 dead-code scanning in CI and local runs?
