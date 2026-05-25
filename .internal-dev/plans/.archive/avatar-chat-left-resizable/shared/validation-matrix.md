# Validation Matrix

| Criterion | Validation | Evidence Required | Playwright Status |
| --- | --- | --- | --- |
| Chat appears on the left | Static HTML/component order review and `AvatarDashboardControllerTest` assertion | Test or rendered HTML proves rail precedes main content or CSS order is explicitly left | Skipped by user request |
| Dashboard fills remaining right width | CSS review of grid columns | `.avatar-shell-grid` uses rail, divider, `minmax(0, 1fr)` main and no shell max cap blocks full width | Skipped by user request |
| Divider drag resizes smoothly | JS static review plus later browser check | Width computed from grid bounds; later visual drag proof when allowed | Skipped by user request |
| Click without drag does not jump | JS static review | Movement threshold or equivalent guard exists before applying/persisting | Skipped by user request |
| Wide screen does not lock at max | JS static review and later wide viewport check | No viewport-right formula; clamp uses grid width and main min width | Skipped by user request |
| Chat follows scroll | CSS static review and later long-page browser check | Rail or valid containing block is sticky on desktop | Skipped by user request |
| Mobile stacks | CSS media query review and later narrow viewport check | Resizer hidden and grid becomes one column | Skipped by user request |
| Existing Avatar tab/edit behavior preserved | `mvn -Dtest=AvatarDashboardControllerTest test` | Passing focused test | N/A |
| App context starts | `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` | Startup reaches ready state before timeout or blocker recorded | N/A |
