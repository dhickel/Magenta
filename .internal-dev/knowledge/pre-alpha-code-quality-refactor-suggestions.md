# Pre-Alpha Code Quality & Refactor Suggestions

**Created:** 2026-05-25 | **Source:** Pre-alpha code quality sweep by Reasonix Code

## Single Responsibility Principle (SRP) Violations — God Classes

These classes have grown to sizes that make them hard to reason about, test, and maintain. Each should be split by domain concern.

| Class | Lines | Size | Concern |
|-------|-------|------|---------|
| `OrchestrationController.java` | 7,739 | 394 KB | Dashboard UI + Plan editor + Job/Assignment management + Workflow CRUD + Entity selection + inline HTML rendering — an entire application in one controller |
| `ChatService.java` | 2,443 | 110 KB | Chat orchestration + plan execution + task execution + tool loops + context management + history + streaming |
| `PlanService.java` | 2,244 | 107 KB | Plan CRUD + run lifecycle + execution + workspace resolution + chat file management |
| `AvatarDashboardComponents.java` | 1,905 | 96 KB | All avatar widget rendering in one class |
| `AvatarDashboardController.java` | ~1,200 | 58 KB | Dashboard layout + todos + daily tasks + notes + calendar + planner + work area explorer + output preview |

**Target:** Controllers <500 lines each. Extract UI rendering into dedicated component services.

## Constructor Proliferation

`ChatService.java` has three constructor overloads with a 22-parameter `@Autowired` constructor, 16 of which are `@Autowired(required = false)`. This hides missing dependencies at compile time and is unmaintainable.

**Recommendation:** Prefer `@ConfigurationProperties` and Lombok's `@RequiredArgsConstructor`. For optional services, use `Optional<>` injection or separate configuration classes.

## Over-Abstraction (YAGNI)

`PlanCompletionValidator` interface has exactly **one** implementation (`ChatModelPlanCompletionValidator`). The `@Autowired(required = false)` injection into `PlanCompletionService` means Spring won't fail if the validator is missing — it silently degrades, returning "Validator model is not configured."

**Recommendation:** Either remove the interface and use the concrete class, or add a second implementation. If the interface stays, remove `required = false` so missing config fails fast at startup.

## Controller Layer Doing Service Work

- `PlanController.java` contains `toDomain()` DTO conversion methods and `buildChatPrompt()` — domain logic in the web layer
- `AvatarDashboardController.java` renders HTML components directly and manages complex state logic

**Recommendation:** Move DTO-to-domain conversion to service/factory layer. Keep controllers to: validate → call service → return response.

## Leaky Data Access

`WorkflowRepository.java` handles both DDL schema migration (25 `ALTER TABLE` statements) and DML CRUD in the same class. Schema evolution is coupled to runtime data access.

**Recommendation:** Extract to a dedicated `WorkflowSchemaManager` or adopt Flyway/Liquibase.

## @Transactional Inconsistency

`@Transactional` appears on repository methods AND some service methods, but `ChatService` (1,800+ lines) has none. Multi-repository operations in ChatService run outside any declared transaction boundary.

**Recommendation:** Annotate service-level methods with `@Transactional(readOnly=true)` for reads and `@Transactional` for writes. Remove from repositories unless they compose multiple data operations.

## AiConfig Overload Tangle

`AiConfig.java` has five constructor overloads with overlapping optional parameters and a misspelled backwards-compatibility field (`summeryModel`).

**Recommendation:** Use `@ConfigurationProperties` with proper prefix binding.
