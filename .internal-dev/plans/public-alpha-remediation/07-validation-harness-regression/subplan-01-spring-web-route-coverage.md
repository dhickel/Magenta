# Subplan 01: Spring Web Route Coverage

## Goal

Add focused Spring web/application-context tests for public REST/SSE groups.

## Implementation Steps

1. Choose `@SpringBootTest`/MockMvc or equivalent based on repo conventions.
2. Cover representative routes for chat, plans, tasks, workflows, jobs, projects, agents, outputs, runtime/settings.
3. Include SSE route binding and event-name assertions where practical.
4. Keep fixtures isolated and fast.

## Validation

Route tests catch binding/status/DTO errors that direct controller tests miss.
