package io.mindspice.magenta2.ai.execution;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActiveTurnRegistryTest {

    @Test
    void acceptsInterruptsDuringModelAndToolPhasesAfterPhaseIsEntered() {
        ActiveTurnRegistry registry = new ActiveTurnRegistry();
        ActiveTurnRegistry.ActiveTurn turn = registry.register("conversation-1");

        assertThat(registry.interrupt(turn.turnId(), "conversation-1", turn.token(), "wait").status())
            .isEqualTo(InterruptStatus.QUEUED_AFTER_TURN);

        turn.phase(ActiveTurnPhase.MODEL_CALL);

        assertThat(registry.interrupt(turn.turnId(), "conversation-1", turn.token(), "Pause the answer").status())
            .isEqualTo(InterruptStatus.ACCEPTED);
        assertThat(turn.pollInterrupt()).contains("Pause the answer");

        turn.phase(ActiveTurnPhase.TOOL_CALL);

        assertThat(registry.interrupt(turn.turnId(), "conversation-1", turn.token(), "Actually search the project").status())
            .isEqualTo(InterruptStatus.ACCEPTED);
        assertThat(turn.pollInterrupt()).contains("Actually search the project");

        turn.phase(ActiveTurnPhase.TOOL_CHECKPOINT);

        assertThat(registry.interrupt(turn.turnId(), "conversation-1", turn.token(), "Use the latest result").status())
            .isEqualTo(InterruptStatus.ACCEPTED);
        assertThat(turn.pollInterrupt()).contains("Use the latest result");
    }

    @Test
    void modelPhaseInterruptInterruptsRegisteredWorkerThread() throws Exception {
        ActiveTurnRegistry registry = new ActiveTurnRegistry();
        ActiveTurnRegistry.ActiveTurn turn = registry.register("conversation-1");
        turn.phase(ActiveTurnPhase.MODEL_CALL);
        CountDownLatch workerRegistered = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            turn.workerThread(Thread.currentThread());
            workerRegistered.countDown();
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        worker.start();
        assertThat(workerRegistered.await(1, TimeUnit.SECONDS)).isTrue();

        assertThat(registry.interrupt(turn.turnId(), "conversation-1", turn.token(), "stop").status())
            .isEqualTo(InterruptStatus.ACCEPTED);

        worker.join(1_000);
        assertThat(worker.isAlive()).isFalse();
    }

    @Test
    void cancelRemovesTurnAndInterruptsRegisteredWorkerThread() throws Exception {
        ActiveTurnRegistry registry = new ActiveTurnRegistry();
        ActiveTurnRegistry.ActiveTurn turn = registry.register("conversation-1");
        CountDownLatch workerRegistered = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            turn.workerThread(Thread.currentThread());
            workerRegistered.countDown();
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        worker.start();
        assertThat(workerRegistered.await(1, TimeUnit.SECONDS)).isTrue();

        registry.cancel(turn.turnId());

        worker.join(1_000);
        assertThat(worker.isAlive()).isFalse();
        assertThat(turn.cancelled()).isTrue();
        assertThat(registry.find(turn.turnId())).isEmpty();
    }

    @Test
    void cancelRemovesActivePlanExecutionRegistration() {
        ActiveTurnRegistry registry = new ActiveTurnRegistry();
        ActiveTurnRegistry.ActiveTurn turn = registry.registerPlanExecution("conversation-1");

        registry.cancel(turn.turnId());

        assertThat(registry.registerPlanExecution("conversation-1").conversationId())
            .isEqualTo("conversation-1");
    }

    @Test
    void rejectsWrongTokenOrInactiveTurn() {
        ActiveTurnRegistry registry = new ActiveTurnRegistry();
        ActiveTurnRegistry.ActiveTurn turn = registry.register("conversation-1");

        assertThat(registry.interrupt(turn.turnId(), "conversation-1", "bad-token", "message").status())
            .isEqualTo(InterruptStatus.INVALID_TOKEN);

        registry.complete(turn.turnId());

        assertThat(registry.interrupt(turn.turnId(), "conversation-1", turn.token(), "message").status())
            .isEqualTo(InterruptStatus.TURN_NOT_ACTIVE);
    }

    @Test
    void rejectsOverlappingPlanExecutionsForSameConversationUntilComplete() {
        ActiveTurnRegistry registry = new ActiveTurnRegistry();
        ActiveTurnRegistry.ActiveTurn turn = registry.registerPlanExecution("conversation-1");

        assertThatThrownBy(() -> registry.registerPlanExecution("conversation-1"))
            .isInstanceOf(ActiveTurnRegistry.PlanExecutionConflictException.class)
            .hasMessageContaining("conversation-1");

        registry.registerPlanExecution("conversation-2");
        registry.complete(turn.turnId());

        assertThat(registry.registerPlanExecution("conversation-1").conversationId())
            .isEqualTo("conversation-1");
    }
}
