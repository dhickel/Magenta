package io.mindspice.magenta2.ai.chat.plan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.model.ChatPlanState;
import io.mindspice.magenta2.ai.chat.repository.ChatMemoryRepository;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.EndpointType;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationTaskContext;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationTaskContextHolder;
import io.mindspice.magenta2.ai.orchestration.workspaces.EffectiveWorkspaceResolver;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactQuery;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceOwnerType;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanServiceTest {

    @Test
    void exitPlanTrimsMessagesCreatedAfterPlanStarted() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        memoryRepository.saveAll("conversation-1", List.of(new UserMessage("before")));
        service.beginPlan("conversation-1");
        memoryRepository.saveAll("conversation-1", List.of(new UserMessage("before"), new UserMessage("during")));

        service.exitPlan("conversation-1");

        assertThat(memoryRepository.findByConversationId("conversation-1"))
            .extracting(message -> message.getText())
            .containsExactly("before");
        assertThat(service.activePlan("conversation-1")).isEmpty();
    }

    @Test
    void runtimeInstructionsExposeCompactPlanState() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-1");
        service.saveDraftPlan(
            "conversation-1",
            "Add plan mode",
            "Plan Mode",
            "Add streamlined planning.",
            "Do not alter existing command names.",
            List.of("Add state", "Inject prompt"),
            List.of("Use slash commands"),
            List.of("Expose evidence")
        );
        service.markExecuting("conversation-1");

        assertThat(service.runtimeInstructions("conversation-1"))
            .contains("Use the approved structured plan below as the execution source of truth")
            .contains("# Plan Mode")
            .contains("## Deliverables")
            .contains("Do not alter existing command names.")
            .contains("1. Add state")
            .contains("Use slash commands")
            .contains("Validation Criteria")
            .contains("Expose evidence")
            .contains("call plan_complete");
    }

    @Test
    void planModeInstructionsAreStandalonePlanningPrompt() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-1");

        assertThat(service.runtimeInstructions("conversation-1"))
            .contains("You are Magenta in PLAN mode")
            .contains("three backend-seeded opening answers")
            .contains("define concrete deliverables")
            .contains("Stay self-iterating while useful planning work remains available")
            .contains("Every PLAN-mode assistant turn that relinquishes control to the user")
            .contains("plan_set_goal")
            .contains("plan_put_item")
            .contains("ask_user_questions")
            .contains("plan_ready_for_approval")
            .contains("validation criteria");
    }

    @Test
    void needsReviewSessionPlanDoesNotResolveAsPlanModeOrReceivePlanningInstructions() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-review");
        service.saveDraftPlan(
            "conversation-review",
            "Validate completion gate",
            "Review Plan",
            "Exercise review state.",
            null,
            List.of("Review state"),
            List.of("Fail validation"),
            List.of("Requires validator approval")
        );
        service.markExecuting("conversation-review");
        service.markNeedsReview("conversation-review");

        assertThat(service.mode("conversation-review")).isEqualTo(io.mindspice.magenta2.ai.chat.model.PlanMode.NORMAL);
        assertThat(service.runtimeInstructions("conversation-review"))
            .doesNotContain("You are Magenta in PLAN mode")
            .doesNotContain("Use the approved structured plan below as the execution source of truth")
            .isEmpty();
    }

    @Test
    void anonymousBeginPlanQueuesThreeOpeningQuestions() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-questions");

        ChatPlanState state = service.view("conversation-questions");
        assertThat(state.promptQuestionCount()).isEqualTo(3);
        assertThat(state.promptQuestion()).isEqualTo("What is the goal?");
        assertThat(service.activePlan("conversation-questions").orElseThrow().pendingQuestions())
            .containsExactly(
                "What is the goal?",
                "What assumptions, details, expectations, constraints, or preferred approach should guide the plan?",
                "What are the expected deliverables?"
            );
    }

    @Test
    void executionReportPersistsEvidenceAndNeedsReviewState() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-1");
        service.saveDraftPlan(
            "conversation-1",
            "Sample posts",
            "Research Plan",
            "Research.",
            null,
            List.of("Sample"),
            List.of(),
            List.of("Sample at least 50 posts")
        );
        service.markExecuting("conversation-1");
        service.recordExecutionReport(
            "conversation-1",
            "Sampled fewer posts than requested.",
            List.of("Actual posts: 40"),
            List.of("Minimum count missed"),
            List.of("Sample at least 50 posts"),
            List.of("summaries.md")
        );
        service.markNeedsReview("conversation-1");

        PlanDefinition plan = service.activePlan("conversation-1").orElseThrow();
        assertThat(plan.status()).isEqualTo(PlanStatus.NEEDS_REVIEW);
        assertThat(plan.executionEvidence())
            .contains("Evidence: Actual posts: 40")
            .contains("Unmet criterion: Sample at least 50 posts");
    }

    @Test
    void failedCompletionValidationPersistsExplicitStatusAndCriterionRemediation() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, objectMapper);
        PlanService service = new PlanService(planRepository, memoryRepository);
        PlanCompletionService completionService = new PlanCompletionService(service, null, null, objectMapper);

        service.beginPlan("conversation-validation");
        service.saveDraftPlan(
            "conversation-validation",
            "Validate every criterion",
            "Validation Plan",
            "Exercise validation feedback.",
            null,
            List.of("Validated result"),
            List.of("Collect evidence"),
            List.of("Criterion A", "Criterion B")
        );
        service.markExecuting("conversation-validation");

        String result = completionService.complete(
            "conversation-validation",
            "Only one criterion has evidence.",
            List.of("Criterion: Criterion A | Evidence: proof"),
            List.of(),
            List.of(),
            List.of(),
            "Done"
        );

        PlanDefinition plan = service.activePlan("conversation-validation").orElseThrow();
        assertThat(plan.status()).isEqualTo(PlanStatus.EXECUTING);
        assertThat(result).contains("Plan validation failed");
        assertThat(plan.validationFeedback())
            .contains("Validator status: FAILED")
            .anySatisfy(item -> assertThat(item)
                .contains("Criterion [failed]: Criterion B")
                .contains("Remediation: Call plan_complete again"));
    }

    @Test
    void completionValidationFailsClosedForUnreadableArtifactPaths() throws Exception {
        Path dataRoot = Files.createDirectories(tempDir.resolve("validator-data"));
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, objectMapper);
        PlanService service = new PlanService(planRepository, memoryRepository);
        PlanCompletionService completionService = new PlanCompletionService(
            service,
            null,
            new AiConfig(null, null, null, null, dataRoot, null, null),
            objectMapper
        );

        service.beginPlan("conversation-artifact-validation");
        service.saveDraftPlan(
            "conversation-artifact-validation",
            "Validate artifact",
            "Artifact Validation Plan",
            "Exercise artifact validation.",
            null,
            List.of("Readable artifact"),
            List.of("Collect evidence"),
            List.of("Artifact path is readable")
        );
        service.markExecuting("conversation-artifact-validation");

        String result = completionService.complete(
            "conversation-artifact-validation",
            "Evidence references a missing artifact.",
            List.of("Criterion: Artifact path is readable | Evidence: missing.md should exist"),
            List.of(),
            List.of(),
            List.of("missing.md"),
            "Done"
        );

        PlanDefinition plan = service.activePlan("conversation-artifact-validation").orElseThrow();
        assertThat(plan.status()).isEqualTo(PlanStatus.EXECUTING);
        assertThat(result).contains("Plan validation failed");
        assertThat(plan.validationFeedback())
            .contains("Validator status: FAILED")
            .anySatisfy(item -> assertThat(item)
                .contains("Artifact 'missing.md' is not readable by the validator"));
    }

    @Test
    void completionValidationFailsClosedForIncompleteValidatorSchema() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, objectMapper);
        PlanService service = new PlanService(planRepository, memoryRepository);
        PlanCompletionService completionService = new PlanCompletionService(service, null, null, objectMapper);

        service.beginPlan("conversation-incomplete-validator");
        service.saveDraftPlan(
            "conversation-incomplete-validator",
            "Validate schema",
            "Validator Schema Plan",
            "Exercise validator schema enforcement.",
            null,
            List.of("Validated result"),
            List.of("Collect evidence"),
            List.of("Criterion A")
        );
        service.markExecuting("conversation-incomplete-validator");
        PlanDefinition plan = service.activePlan("conversation-incomplete-validator").orElseThrow();

        PlanCompletionService.ValidationResult result = completionService.validateResponseForTesting(
            plan,
            "Done",
            """
                {
                  "complete": true,
                  "summary": "Looks complete.",
                  "criteria": [
                    {"criterion": "Validated result", "status": "passed"},
                    {"criterion": "Criterion A", "status": "passed"}
                  ],
                  "findings": [],
                  "remediationSteps": []
                }
                """
        );

        assertThat(result.complete()).isFalse();
        assertThat(result.summary()).isEqualTo("Validator response did not match the required JSON schema.");
        assertThat(result.findings())
            .contains("Validator criteria[0] missing required key: evidence")
            .contains("Validator criteria[1] missing required key: requiredRemediation");
    }

    @Test
    void completionValidationUsesCleanValidatorRequestWithPriorAndCurrentArtifacts() throws Exception {
        Path dataRoot = Files.createDirectories(tempDir.resolve("validator-clean-request"));
        Files.writeString(dataRoot.resolve("prior.md"), "prior artifact content");
        Files.writeString(dataRoot.resolve("current.md"), "current artifact content");
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, objectMapper);
        PlanService service = new PlanService(planRepository, memoryRepository);
        CapturingPlanCompletionValidator validator = new CapturingPlanCompletionValidator("""
            {
              "complete": true,
              "summary": "Verified clean request.",
              "criteria": [
                {
                  "criterion": "Clean Validator Plan",
                  "status": "passed",
                  "evidence": "Approved plan deliverable inspected.",
                  "risk": "",
                  "requiredRemediation": ""
                },
                {
                  "criterion": "Write report",
                  "status": "passed",
                  "evidence": "Artifact contents inspected.",
                  "risk": "",
                  "requiredRemediation": ""
                },
                {
                  "criterion": "Report is readable",
                  "status": "passed",
                  "evidence": "Criterion evidence and artifact contents inspected.",
                  "risk": "",
                  "requiredRemediation": ""
                }
              ],
              "findings": [],
              "remediationSteps": []
            }
            """);
        AiConfig config = new AiConfig(
            "default-agent",
            "executor-key",
            "summary-key",
            "validator-key",
            null,
            10,
            dataRoot,
            null,
            Map.of(
                "validator-key", new ModelConfig("validator-remote", "http://localhost:11434", EndpointType.OLLAMA, 8192, null, null),
                "executor-key", new ModelConfig("executor-remote", "http://localhost:11434", EndpointType.OLLAMA, 8192, null, null)
            ),
            Map.of()
        );
        PlanCompletionService completionService = new PlanCompletionService(service, validator, config, objectMapper);

        memoryRepository.saveAll("conversation-clean-validator", List.of(new UserMessage("broad chat history secret")));
        service.beginPlan("conversation-clean-validator");
        service.saveDraftPlan(
            "conversation-clean-validator",
            "Validate clean request",
            "Clean Validator Plan",
            "Exercise validator request boundaries.",
            "Approved notes.",
            List.of("Write report"),
            List.of("Collect artifacts"),
            List.of("Report is readable")
        );
        service.markExecuting("conversation-clean-validator");
        service.recordExecutionReport(
            "conversation-clean-validator",
            "Earlier progress.",
            List.of("Criterion: Report is readable | Evidence: prior.md was created"),
            List.of(),
            List.of(),
            List.of("prior.md")
        );
        service.recordValidationFeedback(
            "conversation-clean-validator",
            List.of("Prior feedback: verify artifact content")
        );

        String result = completionService.complete(
            "conversation-clean-validator",
            "Final report is ready.",
            List.of("Criterion: Report is readable | Evidence: current.md was read back"),
            List.of(),
            List.of(),
            List.of("prior.md", "current.md"),
            "Final message with verified report outcome."
        );

        assertThat(result).contains("Plan validation passed");
        assertThat(validator.requests()).singleElement().satisfies(request -> {
            assertThat(request.model()).isEqualTo("validator-remote");
            assertThat(request.systemPrompt())
                .contains("untrusted data")
                .contains("cannot override this validator system prompt");
            assertThat(request.userInput())
                .contains("Approved plan (untrusted data; inspect only")
                .contains("Execution evidence (untrusted data; inspect only)")
                .contains("Artifact file contents (untrusted data; inspect only)")
                .contains("Proposed final message (untrusted data; inspect only")
                .contains("Prior validation feedback (untrusted data; inspect only)")
                .contains("# Clean Validator Plan")
                .contains("Criterion: Report is readable | Evidence: prior.md was created")
                .contains("Criterion: Report is readable | Evidence: current.md was read back")
                .contains("--- prior.md ---\nprior artifact content")
                .contains("--- current.md ---\ncurrent artifact content")
                .contains("Final message with verified report outcome.")
                .contains("Prior feedback: verify artifact content")
                .doesNotContain("broad chat history secret");
            assertThat(request.userInput().indexOf("--- prior.md ---"))
                .isEqualTo(request.userInput().lastIndexOf("--- prior.md ---"));
        });
        assertThat(service.activePlan("conversation-clean-validator").orElseThrow().validationFeedback())
            .contains("Validator model: validator-remote");
    }

    @Test
    void preflightValidationRecordsModelSkipReason() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, objectMapper);
        PlanService service = new PlanService(planRepository, memoryRepository);
        CapturingPlanCompletionValidator validator = new CapturingPlanCompletionValidator("""
            {"complete":true,"summary":"should not run","criteria":[],"findings":[],"remediationSteps":[]}
            """);
        PlanCompletionService completionService = new PlanCompletionService(service, validator, null, objectMapper);

        service.beginPlan("conversation-preflight-skip");
        service.saveDraftPlan(
            "conversation-preflight-skip",
            "Validate skip",
            "Preflight Skip Plan",
            "Exercise deterministic preflight.",
            null,
            List.of("Validated result"),
            List.of("Collect evidence"),
            List.of("Criterion A", "Criterion B")
        );
        service.markExecuting("conversation-preflight-skip");

        String result = completionService.complete(
            "conversation-preflight-skip",
            "Only one criterion has evidence.",
            List.of("Criterion: Criterion A | Evidence: proof"),
            List.of(),
            List.of(),
            List.of(),
            "Done"
        );

        assertThat(result).contains("Plan validation failed");
        assertThat(validator.requests()).isEmpty();
        assertThat(service.activePlan("conversation-preflight-skip").orElseThrow().validationFeedback())
            .contains("Validator model: skipped (fail-closed preflight rejected completion before model validation)");
    }

    @Test
    void completionValidationDoesNotFallBackToExecutorModelWhenPlanningModelMissing() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, objectMapper);
        PlanService service = new PlanService(planRepository, memoryRepository);
        CapturingPlanCompletionValidator validator = new CapturingPlanCompletionValidator("""
            {"complete":true,"summary":"should not run","criteria":[],"findings":[],"remediationSteps":[]}
            """);
        AiConfig config = new AiConfig(
            "default-agent",
            "executor-key",
            "summary-key",
            "missing-validator-key",
            null,
            10,
            tempDir,
            null,
            Map.of("executor-key", new ModelConfig("executor-remote", "http://localhost:11434", EndpointType.OLLAMA, 8192, null, null)),
            Map.of()
        );
        PlanCompletionService completionService = new PlanCompletionService(service, validator, config, objectMapper);

        service.beginPlan("conversation-validator-missing");
        service.saveDraftPlan(
            "conversation-validator-missing",
            "Validate model resolution",
            "Missing Validator Plan",
            "Exercise validator model selection.",
            null,
            List.of("Collect evidence"),
            List.of(),
            List.of("Criterion A")
        );
        service.markExecuting("conversation-validator-missing");

        String result = completionService.complete(
            "conversation-validator-missing",
            "Criterion is covered.",
            List.of("Criterion: Criterion A | Evidence: proof"),
            List.of(),
            List.of(),
            List.of(),
            "Done"
        );

        assertThat(result).contains("Plan validation failed");
        assertThat(validator.requests()).isEmpty();
        assertThat(service.activePlan("conversation-validator-missing").orElseThrow().validationFeedback())
            .contains("Validator model: unavailable")
            .contains("Validator summary: Validator model could not be resolved.")
            .anySatisfy(item -> assertThat(item).contains("No planning validator model was available."));
    }

    @Test
    void readyForApprovalExposesTransientMarkdownPlan() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-1");
        answerOpeningQuestions(service, "conversation-1");
        service.updateDraftPlan(
            "conversation-1",
            "Compare GPUs",
            "GPU Plan",
            "Compare options.",
            "Use current benchmarks.",
            List.of("Markdown report"),
            List.of("Research specs", "Write report"),
            List.of("Includes deliverables")
        );
        service.markReadyForApproval("conversation-1");

        assertThat(service.view("conversation-1").approvalMarkdown())
            .contains("# GPU Plan")
            .contains("## Goal")
            .contains("- Markdown report")
            .contains("1. Research specs")
            .contains("## Validation Criteria");
    }

    @Test
    void saveAsTaskRejectsAnonymousPlans() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-save");
        service.updateDraftPlan(
            "conversation-save",
            "Plan goal",
            "Original Plan Title",
            "Summary",
            "Notes",
            List.of("Deliverable"),
            List.of("Step 1"),
            List.of("Criterion 1")
        );

        assertThatThrownBy(() -> service.saveAsTask("conversation-save", "Reusable Task Name"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Anonymous chat plans cannot be saved");
    }

    @Test
    void anonymousPlanUpdateIgnoresStructuredInputsAndOutputs() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-update");
        answerOpeningQuestions(service, "conversation-update");
        PlanDefinition updated = service.updateDraftPlan(
            "conversation-update",
            "Plan goal",
            "Anonymous Plan",
            "Summary",
            "Notes",
            List.of("Deliverable"),
            List.of("typed_input: string required"),
            List.of("typed_output: json required"),
            List.of("Assumption"),
            List.of("Step 1"),
            List.of("Criterion 1")
        );

        assertThat(updated.inputs()).isEmpty();
        assertThat(updated.outputs()).isEmpty();
    }

    @Test
    void queuedPlanningQuestionsAdvanceAndPersistAnswersAsChatMessages() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-1");
        service.askQuestions("conversation-1", List.of("What should we build?", "Any constraints?"));

        assertThat(service.view("conversation-1").promptQuestion()).isEqualTo("What should we build?");
        assertThat(service.view("conversation-1").promptQuestionIndex()).isEqualTo(1);
        assertThat(service.view("conversation-1").promptQuestionCount()).isEqualTo(2);

        service.recordPromptAnswer("conversation-1", "A robust planner.", "Keep it small.");

        assertThat(memoryRepository.findByConversationId("conversation-1"))
            .extracting(message -> message.getText())
            .last()
            .satisfies(text -> assertThat(text)
                .contains("Question: What should we build?")
                .contains("Answer: A robust planner.")
                .contains("Notes: Keep it small."));
        assertThat(service.view("conversation-1").promptQuestion()).isEqualTo("Any constraints?");
        assertThat(service.view("conversation-1").promptQuestionIndex()).isEqualTo(2);

        service.recordPromptAnswer("conversation-1", "No extra orchestration.", null);

        assertThat(service.view("conversation-1").promptQuestion()).isNull();
        assertThat(service.view("conversation-1").promptQuestionCount()).isZero();
    }

    @Test
    void queuedPlanningQuestionsRejectMoreThanFive() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-1");

        assertThatThrownBy(() -> service.askQuestions(
            "conversation-1",
            List.of("One?", "Two?", "Three?", "Four?", "Five?", "Six?")
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at most five");
    }

    @Test
    void taskTemplateCrudAndDraftWorkflow() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        // Begin draft
        PlanDefinition draft = service.beginDraft("draft-conv", "model-a", "model-b");
        assertThat(draft.kind()).isEqualTo(PlanKind.TASK_TEMPLATE);
        assertThat(draft.status()).isEqualTo(PlanStatus.DRAFT);
        assertThat(draft.conversationId()).isEqualTo("draft-conv");

        // Set goal
        PlanDefinition withGoal = service.setTaskGoal("draft-conv", "Research a topic.");
        assertThat(withGoal.goal()).isEqualTo("Research a topic.");
        assertThat(withGoal.planningTask()).isEqualTo("define_outputs");

        // Add input
        PlanDefinition withInput = service.putFieldItem("draft-conv", "input", 1,
            new PlanFieldDefinition("topic", PlanFieldType.STRING, false, "Topic to research", true, null));
        assertThat(withInput.inputs()).extracting(PlanFieldDefinition::name).containsExactly("topic");

        // Add output
        PlanDefinition withOutput = service.putFieldItem("draft-conv", "output", 1,
            new PlanFieldDefinition("notes", PlanFieldType.USER_MESSAGE, false, "Research notes", true, null));
        assertThat(withOutput.outputs()).extracting(PlanFieldDefinition::name).containsExactly("notes");

        // Add step
        PlanDefinition withStep = service.putTextItem("draft-conv", "step", 1, "Collect research notes for <topic>.");
        assertThat(withStep.steps()).hasSize(1);

        // Add validation
        service.putTextItem("draft-conv", "validation_criterion", 1, "Notes contain research findings.");

        // Answer question
        service.recordTaskPromptAnswer("draft-conv", "A topic input is fine.", null, 1);

        // Ready for approval - Need title first
        PlanDefinition titled = planRepository.saveDefinition(
            service.activeDraft("draft-conv").orElseThrow()
                .withTitle("Research Task")
                .withSummary("Research a given topic.")
        );

        // Mark ready
        PlanDefinition ready = service.markTaskReadyForApproval("draft-conv");
        assertThat(ready.status()).isEqualTo(PlanStatus.READY_FOR_APPROVAL);

        // Approve
        PlanDefinition task = service.approveDraft("draft-conv");
        assertThat(task.kind()).isEqualTo(PlanKind.TASK_TEMPLATE);
        assertThat(task.title()).isEqualTo("Research Task");

        // List tasks
        assertThat(service.listTasks()).hasSize(1);
    }

    @Test
    void taskRunLifecycle() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        // Create a finalized task
        PlanDefinition task = service.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Quick Task", "Do it.", "Goal.", null,
            List.of(), List.of(),
            List.of(new PlanFieldDefinition("result", PlanFieldType.STRING, false, "Result", true, null)),
            List.of(), List.of(new PlanStep(1, "Do it.")), List.of("Result present."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null
        ));

        // No required inputs, start directly
        PlanRun run = service.startRun(task.id(), Map.of("dummy", "val"));
        assertThat(run.status()).isEqualTo(PlanRunStatus.RUNNING);

        // Record report
        PlanRun reported = service.recordRunReport(run.id(), "Working", List.of("evidence"));
        assertThat(reported.executionEvidence()).anyMatch(e -> e.contains("Working"));

        // Complete with missing output
        assertThatThrownBy(() -> service.completeRun(run.id(), Map.of(), "done", List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("result");

        // Complete
        PlanRun completed = service.completeRun(run.id(), Map.of("result", "ok"), "done", List.of("Evidence: proof"));
        assertThat(completed.status()).isEqualTo(PlanRunStatus.COMPLETED);
        assertThat(completed.outputValues()).containsEntry("result", "ok");
    }

    @Test
    void modeResolutionHandlesAllStates() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        // No plan -> NORMAL
        assertThat(service.mode("no-plan")).isEqualTo(io.mindspice.magenta2.ai.chat.model.PlanMode.NORMAL);

        // DRAFT plan -> PLAN
        service.beginPlan("conv-1");
        assertThat(service.mode("conv-1")).isEqualTo(io.mindspice.magenta2.ai.chat.model.PlanMode.PLAN);

        // Task draft -> TASK
        service.beginDraft("task-draft", null, null);
        assertThat(service.mode("task-draft")).isEqualTo(io.mindspice.magenta2.ai.chat.model.PlanMode.TASK);
    }

    @Test
    void completedPlanViewUsesNormalModeSoPlanningPanelCanClear() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-1");
        answerOpeningQuestions(service, "conversation-1");
        service.updateDraftPlan(
            "conversation-1",
            "Research breeding stock",
            "Breeding Stock Research",
            "Research candidate strains.",
            null,
            List.of("Research files"),
            List.of("Write strain notes"),
            List.of("Files exist")
        );
        service.markExecuting("conversation-1");
        service.markCompleted("conversation-1", "Done.");

        assertThat(service.view("conversation-1").status()).isEqualTo("COMPLETED");
        assertThat(service.view("conversation-1").mode()).isEqualTo("NORMAL");
    }

    @Test
    void approvalMarkdownRendersOptionalInputsOnlyWhenPresent() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-1");
        answerOpeningQuestions(service, "conversation-1");
        service.updateDraftPlan(
            "conversation-1",
            "Create reusable task",
            "Task Plan",
            "Plan reusable work.",
            null,
            List.of("Updated file"),
            null,
            null,
            List.of("Input names are placeholders."),
            List.of("Inspect target_file", "Apply scoped edit"),
            List.of("The updated file exists")
        );
        service.markReadyForApproval("conversation-1");

        assertThat(service.view("conversation-1").approvalMarkdown())
            .contains("Task Plan")
            .doesNotContain("## Inputs")
            .doesNotContain("## Outputs")
            .contains("- Updated file");
    }

    // ════════════════════════════════════════════════════════════════
    //  Phase 2: Agent context output allocation
    // ════════════════════════════════════════════════════════════════

    @TempDir
    Path tempDir;

    @Test
    void agentContextAllocatesOutputUnderAgentDirectory() throws Exception {
        Path dataRoot = Files.createDirectories(tempDir.resolve("data"));
        WorkspaceDirectoryService dirService = new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, dataRoot, null, null));
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbcTemplate);
        OutputArtifactService artifactService = new OutputArtifactService(
            workspaceRepository, dirService, new ObjectMapper().findAndRegisterModules());

        PlanService service = new PlanService(
            new PlanRepository(jdbcTemplate, new ObjectMapper()),
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper()),
            null, new io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer(),
            dirService, artifactService);

        // Create a task
        PlanDefinition task = service.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Agent Output Task", "Do it.", "Goal.", null,
            List.of(), List.of(),
            List.of(new PlanFieldDefinition("result", PlanFieldType.STRING, false, "Result", true, null)),
            List.of(), List.of(new PlanStep(1, "Do it.")), List.of("Result present."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        // Start run with agent context
        PlanRun run = service.startRun(task.id(), Map.of("result", "ok"),
            new OrchestrationTaskContext("agent-1", "TestAgent", "job-1", null, "ws-1",
                "TASK_RUN", null, null));

        assertThat(run.outputDirectory())
            .isNotNull()
            .contains("agents/agent-1/workspace/outputs/");
        assertThat(Files.isDirectory(Path.of(run.outputDirectory()))).isTrue();
        // Verify it's NOT under system
        assertThat(run.outputDirectory()).doesNotContain("agents/system");

        // Verify temp workspace path is stored
        assertThat(run.tempWorkspacePath())
            .isNotNull()
            .contains("runtime/task-runs/" + run.id());
    }

    @Test
    void chatExecutionWithAgentContextUpdatesHolderWithRunScopedOutputPath() throws Exception {
        Path dataRoot = Files.createDirectories(tempDir.resolve("data-chat"));
        WorkspaceDirectoryService dirService = new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, dataRoot, null, null));
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbcTemplate);
        OutputArtifactService artifactService = new OutputArtifactService(
            workspaceRepository, dirService, new ObjectMapper().findAndRegisterModules());

        PlanService service = new PlanService(
            new PlanRepository(jdbcTemplate, new ObjectMapper()),
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper()),
            null, new io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer(),
            dirService, artifactService);

        PlanDefinition task = service.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Agent Chat Output Task", "Do it.", "Goal.", null,
            List.of(), List.of(),
            List.of(new PlanFieldDefinition("result", PlanFieldType.FILE_PATH, false, "Result", true, null)),
            List.of(), List.of(new PlanStep(1, "Do it.")), List.of("Result present."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        OrchestrationTaskContext context = new OrchestrationTaskContext(
            "agent-1", "TestAgent", "job-1", "project-1", "ws-1",
            "TASK_RUN", null, null);
        OrchestrationTaskContextHolder.set(context);
        try {
            PlanRun run = service.startChatExecution("conversation-1", task.id(), Map.of(), context);
            OrchestrationTaskContext updated = OrchestrationTaskContextHolder.current();

            assertThat(run.outputDirectory()).contains("agents/agent-1/workspace/outputs/");
            assertThat(Path.of(run.outputDirectory()).toRealPath())
                .isNotEqualTo(dirService.projectWorkspace("project-1").toRealPath());
            assertThat(updated.hostWorkspacePath()).isEqualTo(run.tempWorkspacePath());
            assertThat(updated.hostOutputPath()).isEqualTo(run.outputDirectory());
            Path projectLink = Path.of(run.tempWorkspacePath()).resolve("projects/project-1");
            assertThat(Files.isSymbolicLink(projectLink)).isTrue();
            assertThat(projectLink.toRealPath()).isEqualTo(dirService.projectWorkspace("project-1").toRealPath());
        } finally {
            OrchestrationTaskContextHolder.clear();
        }
    }

    @Test
    void startRunPersistsEffectiveWorkspaceIdWithoutMovingCurrentOutputDirectory() throws Exception {
        Path dataRoot = Files.createDirectories(tempDir.resolve("data-effective-workspace"));
        WorkspaceDirectoryService dirService = new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, dataRoot, null, null));
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbcTemplate);
        WorkspaceService workspaceService = new WorkspaceService(
            workspaceRepository,
            new AiConfig(null, null, null, null, dataRoot, null, null));
        EffectiveWorkspaceResolver resolver = new EffectiveWorkspaceResolver(dirService, workspaceService);
        OutputArtifactService artifactService = new OutputArtifactService(
            workspaceRepository, dirService, new ObjectMapper().findAndRegisterModules());

        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(
            planRepository,
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper()),
            null, new io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer(),
            dirService, artifactService, resolver);

        PlanDefinition task = service.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Effective Workspace Task", "Do it.", "Goal.", null,
            List.of(), List.of(), List.of(),
            List.of(), List.of(new PlanStep(1, "Do it.")), List.of("Run starts."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        PlanRun projectRun = service.startRun(task.id(), Map.of(),
            new OrchestrationTaskContext("agent-1", "TestAgent", null, "project-1", "legacy-ws",
                "TASK_RUN", null, null));

        var projectWorkspace = workspaceRepository.findByOwner(WorkspaceOwnerType.PROJECT, "project-1").orElseThrow();
        assertThat(projectRun.workspaceId()).isEqualTo(projectWorkspace.id());
        assertThat(projectRun.workspaceId()).isNotEqualTo("legacy-ws");
        assertThat(projectRun.outputDirectory()).contains("agents/agent-1/workspace/outputs/");
        assertThat(Path.of(projectRun.outputDirectory()).toRealPath())
            .isNotEqualTo(dirService.projectWorkspaceRoot("project-1").toRealPath());
        assertThat(Files.isDirectory(dataRoot.resolve("projects/project-1/workspace/work"))).isTrue();
        assertThat(Files.isDirectory(dataRoot.resolve("projects/project-1/workspace/outputs"))).isTrue();
        assertThat(Files.isDirectory(dataRoot.resolve("projects/project-1/workspace/runs"))).isTrue();
        assertThat(Files.isDirectory(dataRoot.resolve("projects/project-1/workspace/scratch"))).isTrue();

        PlanRun agentRun = service.startRun(task.id(), Map.of(),
            new OrchestrationTaskContext("agent-2", "TestAgent", null, null, "legacy-agent-ws",
                "TASK_RUN", null, null));

        var agentWorkspace = workspaceRepository.findByOwner(WorkspaceOwnerType.AGENT, "agent-2").orElseThrow();
        assertThat(agentRun.workspaceId()).isEqualTo(agentWorkspace.id());
        assertThat(agentRun.workspaceId()).isNotEqualTo("legacy-agent-ws");
        assertThat(agentRun.outputDirectory()).contains("agents/agent-2/workspace/outputs/");
        assertThat(planRepository.findRun(projectRun.id()).orElseThrow().workspaceId()).isEqualTo(projectWorkspace.id());
        assertThat(planRepository.findRun(agentRun.id()).orElseThrow().workspaceId()).isEqualTo(agentWorkspace.id());
    }

    @Test
    void systemContextAllocatesOutputUnderSystemDirectory() throws Exception {
        Path dataRoot = Files.createDirectories(tempDir.resolve("data"));
        WorkspaceDirectoryService dirService = new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, dataRoot, null, null));
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbcTemplate);
        OutputArtifactService artifactService = new OutputArtifactService(
            workspaceRepository, dirService, new ObjectMapper().findAndRegisterModules());

        PlanService service = new PlanService(
            new PlanRepository(jdbcTemplate, new ObjectMapper()),
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper()),
            null, new io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer(),
            dirService, artifactService);

        // Create a task
        PlanDefinition task = service.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "System Output Task", "Do it.", "Goal.", null,
            List.of(), List.of(),
            List.of(new PlanFieldDefinition("result", PlanFieldType.STRING, false, "Result", true, null)),
            List.of(), List.of(new PlanStep(1, "Do it.")), List.of("Result present."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        // Start run WITHOUT agent context (null context → falls back to "system")
        PlanRun run = service.startRun(task.id(), Map.of("result", "ok"), null);

        assertThat(run.outputDirectory())
            .isNotNull()
            .contains("agents/system/workspace/outputs/");
        assertThat(Files.isDirectory(Path.of(run.outputDirectory()))).isTrue();
    }

    @Test
    void completeRunDerivesAgentAttributionFromCurrentWorkspaceOutputPath() throws Exception {
        Path dataRoot = Files.createDirectories(tempDir.resolve("data-attribution"));
        WorkspaceDirectoryService dirService = new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, dataRoot, null, null));
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbcTemplate);
        OutputArtifactService artifactService = new OutputArtifactService(
            workspaceRepository, dirService, new ObjectMapper().findAndRegisterModules());

        PlanService service = new PlanService(
            new PlanRepository(jdbcTemplate, new ObjectMapper()),
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper()),
            null, new io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer(),
            dirService, artifactService);

        PlanDefinition task = service.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Attributed Output Task", "Do it.", "Goal.", null,
            List.of(), List.of(),
            List.of(new PlanFieldDefinition("result", PlanFieldType.STRING, false, "Result", true, null)),
            List.of(), List.of(new PlanStep(1, "Do it.")), List.of("Result present."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        OrchestrationTaskContextHolder.clear();
        PlanRun run = service.startRun(task.id(), Map.of(),
            new OrchestrationTaskContext("agent-1", "TestAgent", null, null, null,
                "TASK_RUN", null, null));

        assertThat(run.outputDirectory()).contains("agents/agent-1/workspace/outputs/");

        service.completeRun(run.id(), Map.of("result", "done"), "Done", List.of());

        RunOutputArtifact artifact = artifactService.artifactsForRun(run.id()).get(0);
        assertThat(artifact.agentId()).isEqualTo("agent-1");
        assertThat(artifact.jobId()).isNull();
        assertThat(artifact.projectId()).isNull();
        assertThat(artifact.workspaceId()).isNull();
        assertThat(artifact.runType()).isEqualTo("TASK_RUN");
        assertThat(artifactService.query(OutputArtifactQuery.of(
            "agent-1", null, null, null, null, null, null, 10)))
            .extracting(RunOutputArtifact::id)
            .containsExactly(artifact.id());
    }

    @Test
    void explicitTaskContextAttributionOverridesOutputPathFallback() throws Exception {
        Path dataRoot = Files.createDirectories(tempDir.resolve("data-explicit-attribution"));
        WorkspaceDirectoryService dirService = new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, dataRoot, null, null));
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbcTemplate);
        OutputArtifactService artifactService = new OutputArtifactService(
            workspaceRepository, dirService, new ObjectMapper().findAndRegisterModules());

        PlanService service = new PlanService(
            new PlanRepository(jdbcTemplate, new ObjectMapper()),
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper()),
            null, new io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer(),
            dirService, artifactService);

        PlanDefinition task = service.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Explicit Attribution Task", "Do it.", "Goal.", null,
            List.of(), List.of(),
            List.of(new PlanFieldDefinition("result", PlanFieldType.STRING, false, "Result", true, null)),
            List.of(), List.of(new PlanStep(1, "Do it.")), List.of("Result present."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        OrchestrationTaskContextHolder.clear();
        PlanRun run = service.startRun(task.id(), Map.of(),
            new OrchestrationTaskContext("path-agent", "PathAgent", null, null, null,
                "TASK_RUN", null, null));
        assertThat(run.outputDirectory()).contains("agents/path-agent/workspace/outputs/");

        OrchestrationTaskContextHolder.set(new OrchestrationTaskContext(
            "explicit-agent", "ExplicitAgent", "job-1", "project-1", "workspace-1",
            "WORKFLOW_RUN", null, null));
        try {
            service.completeRun(run.id(), Map.of("result", "done"), "Done", List.of());
        } finally {
            OrchestrationTaskContextHolder.clear();
        }

        RunOutputArtifact artifact = artifactService.artifactsForRun(run.id()).get(0);
        assertThat(artifact.agentId()).isEqualTo("explicit-agent");
        assertThat(artifact.jobId()).isEqualTo("job-1");
        assertThat(artifact.projectId()).isEqualTo("project-1");
        assertThat(artifact.workspaceId()).isEqualTo("workspace-1");
        assertThat(artifact.runType()).isEqualTo("WORKFLOW_RUN");
    }

    @Test
    void partialTaskContextPreservesProjectAndFallsBackToOutputPathAgent() throws Exception {
        Path dataRoot = Files.createDirectories(tempDir.resolve("data-partial-attribution"));
        WorkspaceDirectoryService dirService = new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, dataRoot, null, null));
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbcTemplate);
        OutputArtifactService artifactService = new OutputArtifactService(
            workspaceRepository, dirService, new ObjectMapper().findAndRegisterModules());

        PlanService service = new PlanService(
            new PlanRepository(jdbcTemplate, new ObjectMapper()),
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper()),
            null, new io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer(),
            dirService, artifactService);

        PlanDefinition task = service.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Partial Attribution Task", "Do it.", "Goal.", null,
            List.of(), List.of(),
            List.of(new PlanFieldDefinition("result", PlanFieldType.STRING, false, "Result", true, null)),
            List.of(), List.of(new PlanStep(1, "Do it.")), List.of("Result present."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        OrchestrationTaskContextHolder.clear();
        PlanRun run = service.startRun(task.id(), Map.of(),
            new OrchestrationTaskContext("path-agent", "PathAgent", null, null, null,
                "TASK_RUN", null, null));
        OrchestrationTaskContextHolder.set(new OrchestrationTaskContext(
            null, null, null, "project-1", null, null, null, null));
        try {
            service.completeRun(run.id(), Map.of("result", "done"), "Done", List.of());
        } finally {
            OrchestrationTaskContextHolder.clear();
        }

        RunOutputArtifact artifact = artifactService.artifactsForRun(run.id()).get(0);
        assertThat(artifact.agentId()).isEqualTo("path-agent");
        assertThat(artifact.projectId()).isEqualTo("project-1");
        assertThat(artifact.runType()).isEqualTo("TASK_RUN");
    }

    @Test
    void workspaceAllocationFailurePersistsFailedRunAndDoesNotContinueWithNullPaths() throws Exception {
        Path dataRoot = Files.createDirectories(tempDir.resolve("allocation-failure-data"));
        WorkspaceDirectoryService dirService = new FailingWorkspaceDirectoryService(dataRoot);
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(
            planRepository,
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper()),
            null, new io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer(),
            dirService, null);

        PlanDefinition task = service.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Allocation Failure Task", "Do it.", "Goal.", null,
            List.of(), List.of(), List.of(),
            List.of(), List.of(new PlanStep(1, "Do it.")), List.of("Run starts clearly."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        PlanRun run = service.startRun(task.id(), Map.of(),
            new OrchestrationTaskContext("agent-1", "TestAgent", "job-1", null, "ws-1",
                "TASK_RUN", null, null));

        assertThat(run.status()).isEqualTo(PlanRunStatus.FAILED);
        assertThat(run.tempWorkspacePath()).isNull();
        assertThat(run.outputDirectory()).isNull();
        assertThat(run.errorText())
            .contains("Filesystem workspace/output allocation failed")
            .contains("simulated temp allocation failure");
        assertThat(run.executionEvidence()).anySatisfy(entry -> assertThat(entry)
            .contains("Failure: Filesystem workspace/output allocation failed")
            .contains("simulated temp allocation failure"));

        PlanRun persisted = planRepository.findRun(run.id()).orElseThrow();
        assertThat(persisted.status()).isEqualTo(PlanRunStatus.FAILED);
        assertThat(persisted.errorText()).isEqualTo(run.errorText());
        assertThatThrownBy(() -> service.completeRun(run.id(), Map.of(), "done", List.of()))
            .hasMessageContaining("complete is available only while a run is active");
    }

    @Test
    void completeRunCleansTempDirectoryAndDetectsLooseArtifacts() throws Exception {
        Path dataRoot = Files.createDirectories(tempDir.resolve("data2"));
        WorkspaceDirectoryService dirService = new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, dataRoot, null, null));
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbcTemplate);
        OutputArtifactService artifactService = new OutputArtifactService(
            workspaceRepository, dirService, new ObjectMapper().findAndRegisterModules());

        PlanService service = new PlanService(
            new PlanRepository(jdbcTemplate, new ObjectMapper()),
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper()),
            null, new io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer(),
            dirService, artifactService);

        PlanDefinition task = service.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Cleanup Task", "Do it.", "Goal.", null,
            List.of(), List.of(),
            List.of(new PlanFieldDefinition("result", PlanFieldType.STRING, false, "Result", true, null)),
            List.of(), List.of(new PlanStep(1, "Do it.")), List.of("Result present."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        PlanRun run = service.startRun(task.id(), Map.of("result", "ok"));
        String tempPath = run.tempWorkspacePath();
        assertThat(tempPath).isNotNull();
        assertThat(Files.isDirectory(Path.of(tempPath))).isTrue();

        // Create a loose file in the output dir
        Path outputDir = Path.of(run.outputDirectory());
        Files.writeString(outputDir.resolve("extra.txt"), "loose content");

        // Complete the run
        PlanRun completed = service.completeRun(run.id(), Map.of("result", "done"), "Done", List.of());

        assertThat(completed.status()).isEqualTo(PlanRunStatus.COMPLETED);
        // Temp directory should be cleaned
        assertThat(Files.exists(Path.of(tempPath))).isFalse();
        // Loose artifact should be discovered
        List<?> artifacts = artifactService.artifactsForRun(run.id()).stream()
            .filter(a -> a.fileName().equals("extra.txt"))
            .toList();
        assertThat(artifacts).hasSize(1);
    }

    @Test
    void completeRunDoesNotDiscoverConversationChatFilesAsTaskArtifacts() throws Exception {
        Path dataRoot = Files.createDirectories(tempDir.resolve("data-chat-file-separation"));
        Path chatFile = Files.createDirectories(dataRoot.resolve("chats/conversation-1/files"))
            .resolve("uploaded-notes.md");
        Files.writeString(chatFile, "# chat upload\n");

        WorkspaceDirectoryService dirService = new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, dataRoot, null, null));
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbcTemplate);
        OutputArtifactService artifactService = new OutputArtifactService(
            workspaceRepository, dirService, new ObjectMapper().findAndRegisterModules());

        PlanService service = new PlanService(
            new PlanRepository(jdbcTemplate, new ObjectMapper()),
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper()),
            null, new io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer(),
            dirService, artifactService);

        PlanDefinition task = service.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Chat File Separation Task", "Do it.", "Goal.", null,
            List.of(), List.of(),
            List.of(new PlanFieldDefinition("result", PlanFieldType.STRING, false, "Result", true, null)),
            List.of(), List.of(new PlanStep(1, "Do it.")), List.of("Result present."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        PlanRun run = service.startRun(task.id(), Map.of());
        service.completeRun(run.id(), Map.of("result", "done"), "Done", List.of());

        assertThat(Files.exists(chatFile)).isTrue();
        assertThat(artifactService.artifactsForRun(run.id()))
            .extracting(RunOutputArtifact::fileName)
            .containsExactly("result.txt")
            .doesNotContain("uploaded-notes.md");
    }

    // ── Phase 03: Draft/finalize lifecycle tests ──

    @Test
    void saveTaskPreservesDraftStatus() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, null);

        PlanDefinition draft = service.beginDraft("conv-draft", null, null);
        assertThat(draft.status()).isEqualTo(PlanStatus.DRAFT);

        // Save with DRAFT status should persist as DRAFT
        PlanDefinition saved = service.saveTask(draft);
        assertThat(saved.status()).isEqualTo(PlanStatus.DRAFT);

        // Reload and check
        PlanDefinition reloaded = service.getTask(saved.id());
        assertThat(reloaded.status()).isEqualTo(PlanStatus.DRAFT);
    }

    @Test
    void saveTaskPreservesReadyForApprovalStatus() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, null);

        PlanDefinition draft = service.beginDraft("conv-rfa", null, null);
        PlanDefinition rfa = planRepository.saveDefinition(draft.withStatus(PlanStatus.READY_FOR_APPROVAL));

        PlanDefinition saved = service.saveTask(rfa);
        assertThat(saved.status()).isEqualTo(PlanStatus.READY_FOR_APPROVAL);
    }

    @Test
    void finalizeTaskSetsApprovedAndValidatesStatus() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, null);

        PlanDefinition draft = service.beginDraft("conv-finalize", null, null);
        // Give it required fields for validation
        PlanDefinition complete = planRepository.saveDefinition(draft
            .withTitle("Completed Task")
            .withGoal("Test goal")
            .withSteps(List.of(new PlanStep(1, "Step 1")))
            .withOutputs(List.of(new PlanFieldDefinition("result", PlanFieldType.STRING, false, "Output", true, null)))
            .withValidationCriteria(List.of("Criterion 1")));

        PlanDefinition finalized = service.finalizeTask(complete.id());
        assertThat(finalized.status()).isEqualTo(PlanStatus.APPROVED);
    }

    @Test
    void saveTaskDefaultsToApprovedForOtherStatuses() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, null);

        PlanDefinition draft = service.beginDraft("conv-default", null, null);
        PlanDefinition executing = planRepository.saveDefinition(draft.withStatus(PlanStatus.EXECUTING));

        // EXECUTING should default to APPROVED in saveTask
        PlanDefinition saved = service.saveTask(executing);
        assertThat(saved.status()).isEqualTo(PlanStatus.APPROVED);
    }

    @Test
    void createTaskViaBeginDraftReturnsDraft() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, null);

        PlanDefinition newDraft = service.beginDraft("conv-create", null, null);
        assertThat(newDraft.status()).isEqualTo(PlanStatus.DRAFT);
        assertThat(newDraft.kind()).isEqualTo(PlanKind.TASK_TEMPLATE);
        assertThat(newDraft.title()).isEqualTo("Untitled Task");
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true);
        return new JdbcTemplate(dataSource);
    }

    private void answerOpeningQuestions(PlanService service, String conversationId) {
        service.recordPromptAnswer(conversationId, "Goal", null);
        service.recordPromptAnswer(conversationId, "Guidance", null);
        service.recordPromptAnswer(conversationId, "Deliverables", null);
    }

    private static final class CapturingPlanCompletionValidator implements PlanCompletionValidator {
        private final String response;
        private final List<ValidationRequest> requests = new java.util.ArrayList<>();

        private CapturingPlanCompletionValidator(String response) {
            this.response = response;
        }

        @Override
        public ValidationResponse validate(ValidationRequest request) {
            requests.add(request);
            return new ValidationResponse(request.model(), response);
        }

        private List<ValidationRequest> requests() {
            return requests;
        }
    }

    private static final class FailingWorkspaceDirectoryService extends WorkspaceDirectoryService {
        private FailingWorkspaceDirectoryService(Path dataRoot) throws Exception {
            super(new AiConfig(null, null, null, null, dataRoot, null, null));
        }

        @Override
        public Path taskTemp(String runId) {
            throw new IllegalStateException("simulated temp allocation failure");
        }
    }
}
