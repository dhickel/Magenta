package io.mindspice.magenta2.ai.execution;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MagentaWorkExecutorTest {

    @Test
    void runsHigherPriorityQueuedWorkFirst() throws Exception {
        MagentaWorkExecutor executor = new MagentaWorkExecutor(Map.of(
            MagentaWorkKind.CHAT_TURN, new MagentaWorkExecutor.LaneSettings("test-chat-", 1, 10)
        ));
        CountDownLatch releaseFirst = new CountDownLatch(1);
        StringBuilder order = new StringBuilder();

        executor.submitChat("conversation-1", 1, "first", () -> {
            releaseFirst.await(2, TimeUnit.SECONDS);
            order.append("first ");
            return null;
        });
        executor.submitChat("conversation-2", 10, "high", () -> {
            order.append("high ");
            return null;
        });
        var low = executor.submitChat("conversation-3", 2, "low", () -> {
            order.append("low");
            return null;
        });

        releaseFirst.countDown();
        low.get(2, TimeUnit.SECONDS);

        assertThat(order.toString()).isEqualTo("first high low");
    }

    @Test
    void coordinatorSerializesSameConversationWithoutBlockingOtherConversations() throws Exception {
        MagentaWorkExecutor executor = new MagentaWorkExecutor(Map.of(
            MagentaWorkKind.CHAT_TURN, new MagentaWorkExecutor.LaneSettings("test-chat-", 2, 10)
        ));
        ConversationTurnCoordinator coordinator = new ConversationTurnCoordinator(executor);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch otherStarted = new CountDownLatch(1);
        AtomicInteger sameConversationRunning = new AtomicInteger();
        AtomicInteger maxSameConversationRunning = new AtomicInteger();

        coordinator.submit("conversation-1", 100, "first", () -> {
            int running = sameConversationRunning.incrementAndGet();
            maxSameConversationRunning.accumulateAndGet(running, Math::max);
            firstStarted.countDown();
            releaseFirst.await(2, TimeUnit.SECONDS);
            sameConversationRunning.decrementAndGet();
            return null;
        });
        var secondSame = coordinator.submit("conversation-1", 100, "second", () -> {
            int running = sameConversationRunning.incrementAndGet();
            maxSameConversationRunning.accumulateAndGet(running, Math::max);
            sameConversationRunning.decrementAndGet();
            return null;
        });
        var other = coordinator.submit("conversation-2", 100, "other", () -> {
            otherStarted.countDown();
            return null;
        });

        assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(otherStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(secondSame.isDone()).isFalse();

        releaseFirst.countDown();
        secondSame.get(2, TimeUnit.SECONDS);
        other.get(2, TimeUnit.SECONDS);

        assertThat(maxSameConversationRunning.get()).isEqualTo(1);
    }

    @Test
    void coordinatorRejectedTurnDoesNotPoisonConversationQueue() throws Exception {
        MagentaWorkExecutor executor = new MagentaWorkExecutor(Map.of(
            MagentaWorkKind.CHAT_TURN, new MagentaWorkExecutor.LaneSettings("test-chat-", 1, 0)
        ));
        ConversationTurnCoordinator coordinator = new ConversationTurnCoordinator(executor);
        CountDownLatch laneOccupied = new CountDownLatch(1);
        CountDownLatch releaseLane = new CountDownLatch(1);

        CompletableFuture<Void> blockingTurn = executor.submitChat("other-conversation", 100, "blocking", () -> {
            laneOccupied.countDown();
            releaseLane.await(2, TimeUnit.SECONDS);
            return null;
        });
        assertThat(laneOccupied.await(2, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> rejectedTurn = coordinator.submit("conversation-1", 100, "rejected", () -> null);

        assertThat(rejectedTurn).isCompletedExceptionally();
        assertThatThrownBy(rejectedTurn::get)
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(java.util.concurrent.RejectedExecutionException.class);

        releaseLane.countDown();
        blockingTurn.get(2, TimeUnit.SECONDS);

        CompletableFuture<String> laterTurn = coordinator.submit("conversation-1", 100, "later", () -> "completed");

        assertThat(laterTurn.get(2, TimeUnit.SECONDS)).isEqualTo("completed");
    }
}
