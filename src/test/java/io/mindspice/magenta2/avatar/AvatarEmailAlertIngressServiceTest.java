package io.mindspice.magenta2.avatar;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.mindspice.magenta2.ai.orchestration.runtime.EventType;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationEvent;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationEventService;
import io.mindspice.magenta2.avatar.AvatarEmailAlertIngressService.EmailAlertRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvatarEmailAlertIngressServiceTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void rejectsMissingOrBadToken() {
        AvatarEmailAlertIngressService service = new AvatarEmailAlertIngressService(
            mock(OrchestrationEventService.class),
            "secret-token"
        );
        EmailAlertRequest request = request();

        assertThatThrownBy(() -> service.ingest(null, request))
            .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> service.ingest("bad-token", request))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void publishesOnlyRedactedPayload() {
        OrchestrationEventService eventService = mock(OrchestrationEventService.class);
        when(eventService.publish(eq(EventType.EMAIL_ALERT_RECEIVED), eq("EMAIL_ALERT"), any(), any()))
            .thenReturn(new OrchestrationEvent("event-1", EventType.EMAIL_ALERT_RECEIVED, "EMAIL_ALERT", "source-1", Map.of(), Instant.now(), null));
        AvatarEmailAlertIngressService service = new AvatarEmailAlertIngressService(eventService, "secret-token");

        service.ingest("secret-token", request());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eventService).publish(
            eq(EventType.EMAIL_ALERT_RECEIVED),
            eq("EMAIL_ALERT"),
            any(),
            payloadCaptor.capture()
        );
        Map<String, Object> payload = payloadCaptor.getValue();
        String serialized = payload.toString();

        assertThat(payload)
            .containsKeys("messageIdHash", "fromDomain", "fromAddressHash", "subjectSnippet", "receivedAt", "labels", "importance", "threadKeyHash")
            .containsEntry("fromDomain", "example.com")
            .containsEntry("importance", "high");
        assertThat(payload.get("messageIdHash").toString()).hasSize(64);
        assertThat(payload.get("fromAddressHash").toString()).hasSize(64);
        assertThat(payload.get("threadKeyHash").toString()).hasSize(64);
        assertThat(serialized)
            .doesNotContain("raw-message-id")
            .doesNotContain("alice@example.com")
            .doesNotContain("secret body")
            .doesNotContain("secret-token")
            .doesNotContain("thread-raw-key");
    }

    private EmailAlertRequest request() {
        return new EmailAlertRequest(
            "raw-message-id",
            "Alice <alice@example.com>",
            "A subject that can be shown as a short snippet",
            "2026-05-22T18:00:00Z",
            List.of("inbox", "important"),
            "high",
            "thread-raw-key",
            "secret body that must never be stored"
        );
    }
}
