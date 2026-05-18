# Operational UI Domain Gate Validation

The operational UI domain gate should scan for stale runtime wording beyond user-visible labels. Active comments, parameter names, and test fixtures can still preserve misleading Docker/container-era assumptions even after UI text is cleaned up.

Useful focused scan terms from the Domain 06 fix pass:

```bash
rg -n "wakeContainer|container-result|container output|Replaces container status" src/main src/test
rg -n -i "docker|podman|container-runtime|docker-java|magenta\\.docker|agent-docker-status" src/main src/test pom.xml docs README.md config --glob '!**/target/**'
```

Generic `container` matches in frontend DOM/layout terminology are acceptable when they describe HTML or CSS structure rather than runtime provenance.
