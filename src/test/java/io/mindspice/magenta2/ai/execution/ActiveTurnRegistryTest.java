package io.mindspice.magenta2.ai.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveTurnRegistryTest {

    @Test
    void acceptsInterruptsOnlyDuringToolPhases() {
        ActiveTurnRegistry registry = new ActiveTurnRegistry();
        ActiveTurnRegistry.ActiveTurn turn = registry.register("conversation-1");

        assertThat(registry.interrupt(turn.turnId(), "conversation-1", turn.token(), "wait").status())
            .isEqualTo(InterruptStatus.QUEUED_AFTER_TURN);

        turn.phase(ActiveTurnPhase.TOOL_CALL);

        assertThat(registry.interrupt(turn.turnId(), "conversation-1", turn.token(), "Actually search the project").status())
            .isEqualTo(InterruptStatus.ACCEPTED);
        assertThat(turn.pollInterrupt()).contains("Actually search the project");
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
}
