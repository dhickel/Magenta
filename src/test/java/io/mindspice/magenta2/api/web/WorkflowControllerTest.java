package io.mindspice.magenta2.api.web;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.mindspice.magenta2.ai.chat.workflow.WorkflowDefinition;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowRun;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowRunStatus;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowService;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowStep;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowStepRun;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowStepRunStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunResult;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowControllerTest {

    @Test
    void createRejectsBlankTitle() {
        WorkflowController controller = new WorkflowController(stubService(), null);
        WorkflowDefinition blankTitle = new WorkflowDefinition(
            null, "  ", "summary", List.of(), null, null
        );

        assertThatThrownBy(() -> controller.create(blankTitle))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    @Test
    void getReturns404ForMissingId() {
        WorkflowController controller = new WorkflowController(missingService(), null);

        assertThatThrownBy(() -> controller.get("non-existent"))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            });
    }

    @Test
    void getRunReturns404ForMissingRunId() {
        WorkflowController controller = new WorkflowController(missingRunService(), null);

        assertThatThrownBy(() -> controller.getRun("non-existent-run"))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            });
    }

    @Test
    void updateRejectsBlankTitle() {
        WorkflowController controller = new WorkflowController(stubService(), null);
        WorkflowDefinition blankTitle = new WorkflowDefinition(
            null, "  ", "summary", List.of(), null, null
        );

        assertThatThrownBy(() -> controller.update("workflow-1", blankTitle))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    @Test
    void streamRunAcceptsNullBody() {
        WorkflowController controller = new WorkflowController(
            stubService(),
            new StubOrchestrationRunService(true)
        );

        assertThat(controller.streamRun("workflow-1", null)).isNotNull();
    }

    @Test
    void listReturnsWorkflows() {
        WorkflowController controller = new WorkflowController(stubService(), null);

        List<WorkflowDefinition> result = controller.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Test Workflow");
    }

    @Test
    void streamRunEmitterHasNoTimeout() {
        WorkflowController controller = new WorkflowController(
            stubService(),
            new StubOrchestrationRunService(true)
        );

        SseEmitter emitter = controller.streamRun("workflow-1", null);

        assertThat(emitter.getTimeout()).isZero();
    }

    @Test
    void streamRunHandlesServiceError() {
        WorkflowController controller = new WorkflowController(
            failingService(),
            new StubOrchestrationRunService(true)
        );

        SseEmitter emitter = controller.streamRun("non-existent", null);

        assertThat(emitter).isNotNull();
    }

    @Test
    void streamRunReturnsBeforeWorkflowExecutionCompletesAndEmitsTerminalStatus() throws Exception {
        BlockingWorkflowService workflowService = new BlockingWorkflowService();
        WorkflowController controller = new WorkflowController(workflowService, new StubOrchestrationRunService(false));

        CompletableFuture<SseEmitter> response = CompletableFuture.supplyAsync(() ->
            controller.streamRun("wf-1", null)
        );

        SseEmitter emitter = response.get(200, TimeUnit.MILLISECONDS);
        CapturedSse captured = initializeEmitter(emitter);
        assertThat(workflowService.executionStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(captured.awaitEventContaining("event=started", 1, TimeUnit.SECONDS)).isTrue();
        assertThat(captured.awaitEventContaining("event=step_started", 1, TimeUnit.SECONDS)).isTrue();
        assertThat(workflowService.releaseExecution.getCount()).isEqualTo(1);
        workflowService.releaseExecution.countDown();
        assertThat(captured.completed.await(1, TimeUnit.SECONDS)).isTrue();

        String events = String.join("\n", captured.events);
        assertThat(events).contains("started");
        assertThat(events).contains("step_started");
        assertThat(events).contains("step_completed");
        assertThat(events).contains("completed");
        assertThat(events).contains("COMPLETED");
    }

    @Test
    void streamRunCompletesQuietlyWhenClientSendFails() throws Exception {
        BlockingWorkflowService workflowService = new BlockingWorkflowService();
        WorkflowController controller = new WorkflowController(workflowService, new StubOrchestrationRunService(false));

        SseEmitter emitter = controller.streamRun("wf-1", null);
        CapturedSse captured = initializeEmitterWithFailingSend(emitter);

        assertThat(captured.completed.await(1, TimeUnit.SECONDS)).isTrue();
        workflowService.releaseExecution.countDown();
    }

    private static WorkflowService stubService() {
        return new WorkflowService(null, null, null) {
            @Override
            public List<WorkflowDefinition> listWorkflows() {
                return List.of(new WorkflowDefinition(
                    "wf-1", "Test Workflow", "summary",
                    List.of(new WorkflowStep("step_1", "task-1", List.of())),
                    null, null
                ));
            }

            @Override
            public WorkflowDefinition saveWorkflow(WorkflowDefinition workflow) {
                if (workflow.title() == null || workflow.title().isBlank()) {
                    throw new IllegalArgumentException("title is required");
                }
                return new WorkflowDefinition(
                    "wf-1", workflow.title(), workflow.summary(), workflow.steps(), null, null
                );
            }

            @Override
            public WorkflowDefinition getWorkflow(String workflowId) {
                if (!"wf-1".equals(workflowId)) {
                    throw new IllegalStateException("workflow not found: " + workflowId);
                }
                return listWorkflows().get(0);
            }

            @Override
            public WorkflowRun getRun(String runId) {
                if (!"run-1".equals(runId)) {
                    throw new IllegalStateException("run not found: " + runId);
                }
                return null;
            }
        };
    }

    private static WorkflowService missingService() {
        return new WorkflowService(null, null, null) {
            @Override
            public WorkflowDefinition getWorkflow(String workflowId) {
                throw new IllegalStateException("workflow not found: " + workflowId);
            }
        };
    }

    private static WorkflowService missingRunService() {
        return new WorkflowService(null, null, null) {
            @Override
            public WorkflowRun getRun(String runId) {
                throw new IllegalStateException("run not found: " + runId);
            }
        };
    }

    private static WorkflowService failingService() {
        return new WorkflowService(null, null, null) {
            @Override
            public WorkflowDefinition getWorkflow(String workflowId) {
                return new WorkflowDefinition(
                    workflowId, "Failing Workflow", "summary",
                    List.of(new WorkflowStep("step_1", "task-1", List.of())),
                    null, null
                );
            }

            @Override
            public WorkflowRun runSynchronously(String workflowId) {
                throw new IllegalStateException("execution failed");
            }
        };
    }

    private static class StubOrchestrationRunService extends OrchestrationRunService {
        private final boolean hasContext;

        StubOrchestrationRunService(boolean hasContext) {
            super(null, null, null);
            this.hasContext = hasContext;
        }

        @Override
        public OrchestrationRunResult runWorkflow(String workflowId, io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunContext context) {
            return new OrchestrationRunResult(
                new WorkAssignment("assign-1", "agent-1", "job-1", null,
                    io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType.WORKFLOW_RUN,
                    0, OrchestrationStatus.COMPLETED, null, null, 0,
                    Map.of(), Map.of(), Map.of(), Map.of(), null, null, null, null, null, null, null),
                "run-1", Map.of()
            );
        }
    }

    private static final class BlockingWorkflowService extends WorkflowService {
        private final CountDownLatch executionStarted = new CountDownLatch(1);
        private final CountDownLatch releaseExecution = new CountDownLatch(1);

        BlockingWorkflowService() {
            super(null, null, null);
        }

        @Override
        public WorkflowDefinition getWorkflow(String workflowId) {
            return new WorkflowDefinition(
                "wf-1", "Blocking Workflow", "summary",
                List.of(new WorkflowStep("step_1", "task-1", List.of())),
                null, null
            );
        }

        @Override
        public WorkflowRun runSynchronously(String workflowId) {
            executionStarted.countDown();
            try {
                releaseExecution.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
            return new WorkflowRun(
                "run-1", "wf-1", WorkflowRunStatus.COMPLETED, getWorkflow(workflowId),
                List.of(new WorkflowStepRun(
                    "step_1", "task-1", "task-run-1",
                    WorkflowStepRunStatus.COMPLETED, Map.of(), Map.of("output", "result"), null
                )),
                Map.of("output", "result"), "completed", null,
                Instant.now(), Instant.now(), Instant.now(), Instant.now()
            );
        }
    }

    private CapturedSse initializeEmitter(SseEmitter emitter) throws Exception {
        CapturedSse captured = new CapturedSse();
        Class<?> handlerType = Class.forName(
            "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter$Handler"
        );
        Object handler = Proxy.newProxyInstance(
            handlerType.getClassLoader(),
            new Class<?>[] { handlerType },
            (proxy, method, args) -> {
                if ("send".equals(method.getName()) && args[0] instanceof Set<?> set) {
                    for (Object item : set) {
                        captured.add(String.valueOf(
                            item.getClass().getMethod("getData").invoke(item)
                        ));
                    }
                } else if ("send".equals(method.getName())) {
                    captured.add(String.valueOf(args[0]));
                } else if ("complete".equals(method.getName())) {
                    captured.completed.countDown();
                } else if ("completeWithError".equals(method.getName())) {
                    captured.completed.countDown();
                }
                return null;
            }
        );
        var initialize = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class
            .getDeclaredMethod("initialize", handlerType);
        initialize.setAccessible(true);
        initialize.invoke(emitter, handler);
        return captured;
    }

    private CapturedSse initializeEmitterWithFailingSend(SseEmitter emitter) throws Exception {
        CapturedSse captured = new CapturedSse();
        Class<?> handlerType = Class.forName(
            "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter$Handler"
        );
        Object handler = Proxy.newProxyInstance(
            handlerType.getClassLoader(),
            new Class<?>[] { handlerType },
            (proxy, method, args) -> {
                if ("send".equals(method.getName())) {
                    throw new IOException("client disconnected");
                } else if ("complete".equals(method.getName())) {
                    captured.completed.countDown();
                } else if ("completeWithError".equals(method.getName())) {
                    captured.completed.countDown();
                }
                return null;
            }
        );
        var initialize = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class
            .getDeclaredMethod("initialize", handlerType);
        initialize.setAccessible(true);
        initialize.invoke(emitter, handler);
        return captured;
    }

    private static final class CapturedSse {
        private final List<String> events = java.util.Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch completed = new CountDownLatch(1);

        private void add(String event) {
            events.add(event);
            synchronized (events) {
                events.notifyAll();
            }
        }

        private boolean awaitEventContaining(String value, long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            synchronized (events) {
                while (events.stream().noneMatch(event -> event.contains(value))) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        return false;
                    }
                    TimeUnit.NANOSECONDS.timedWait(events, remaining);
                }
                return true;
            }
        }
    }
}
