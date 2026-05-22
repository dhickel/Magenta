package io.mindspice.magenta2.api.avatar;

import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationEvent;
import io.mindspice.magenta2.avatar.AvatarEmailAlertIngressService;
import io.mindspice.magenta2.avatar.AvatarEmailAlertIngressService.EmailAlertRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AvatarEmailAlertController {
    public static final String TOKEN_HEADER = "X-Magenta-Avatar-Email-Token";

    private final AvatarEmailAlertIngressService ingressService;

    public AvatarEmailAlertController(AvatarEmailAlertIngressService ingressService) {
        this.ingressService = ingressService;
    }

    @PostMapping("/api/avatar/email-alerts")
    public OrchestrationEvent ingest(
        @RequestHeader(value = TOKEN_HEADER, required = false) String token,
        @RequestBody EmailAlertRequest request
    ) {
        try {
            return ingressService.ingest(token, request);
        } catch (SecurityException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }
}
