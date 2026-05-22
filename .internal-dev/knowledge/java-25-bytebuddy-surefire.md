# Java 25 Byte Buddy Surefire

## Topic

Java 25 test runs that use Mockito inline mocks need Byte Buddy experimental class-file support until the dependency stack officially supports Java 25 class file version 69.

## Source References

- `pom.xml`
- Phase 03 focused validation on 2026-05-22

## Key Takeaways

- Without `-Dnet.bytebuddy.experimental=true` in the forked test JVM, Mockito inline mocks can fail before tests run with `Java 25 (69) is not supported by the current version of Byte Buddy`.
- Setting `MAVEN_OPTS` on the Maven launcher is not sufficient for forked Surefire tests in this project.
- Configure Surefire `argLine` so the property is present in the test JVM.

## Engine Relevance

Phase 03 agent operational tool tests use class-level Mockito mocks around Spring services. The local Java 25 runtime needs the Surefire argLine for focused and broad `mvn test` validation to be repeatable.

## Open Questions

- Remove the experimental flag once Spring Boot's managed Mockito/Byte Buddy stack officially supports Java 25 class files without it.
