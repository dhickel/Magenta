package io.mindspice.magenta.runtime.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.tools.ToolRequest;

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SecurityManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> FILE_TOOLS = Set.of("read_file", "write_file", "delete_file", "grep_files", "search_replace");

    private final ToolPolicy defaultPolicy;
    private final ApprovalCallback approvalCallback;
    private final Path workspaceRoot;
    private final ConcurrentMap<UUID, ToolPolicy> sessionPolicies = new ConcurrentHashMap<>();

    public SecurityManager(
            RuntimeConfig.SecurityPolicyConfig securityConfig,
            Path workspaceRoot,
            ApprovalCallback approvalCallback
    ) {
        this.defaultPolicy = ToolPolicy.from(securityConfig == null
                ? RuntimeConfig.SecurityPolicyConfig.defaults()
                : securityConfig);
        this.workspaceRoot = workspaceRoot == null
                ? Path.of("").toAbsolutePath().normalize()
                : workspaceRoot.toAbsolutePath().normalize();
        this.approvalCallback = approvalCallback;
    }

    public void initializePolicy(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        sessionPolicies.putIfAbsent(sessionId, defaultPolicy);
    }

    public void copyPolicy(UUID sourceSessionId, UUID targetSessionId) {
        Objects.requireNonNull(sourceSessionId, "sourceSessionId");
        Objects.requireNonNull(targetSessionId, "targetSessionId");
        sessionPolicies.put(targetSessionId, toolPolicy(sourceSessionId));
    }

    public void setToolPolicy(UUID sessionId, ToolPolicy policy) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(policy, "policy");
        sessionPolicies.put(sessionId, policy);
    }

    public ToolPolicy toolPolicy(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        return sessionPolicies.getOrDefault(sessionId, defaultPolicy);
    }

    public void clearPolicy(UUID sessionId) {
        if (sessionId == null) {
            return;
        }
        sessionPolicies.remove(sessionId);
    }

    public Decision authorize(ToolRequest request, Set<String> agentToolIds) {
        if (request == null || request.toolCall() == null) {
            return new Decision(DecisionCode.VALIDATION_ERROR, false, "Missing tool request", RuntimeConfig.SecurityMode.DENY_ALL);
        }

        UUID sessionId;
        try {
            sessionId = UUID.fromString(request.sessionId());
        } catch (Exception ignored) {
            return new Decision(DecisionCode.VALIDATION_ERROR, false, "Invalid tool request session id", RuntimeConfig.SecurityMode.DENY_ALL);
        }

        ToolPolicy policy = toolPolicy(sessionId);
        RuntimeConfig.SecurityMode mode = policy.mode();
        String toolName = request.toolCall().name() == null ? "" : request.toolCall().name().trim();

        if (toolName.isEmpty()) {
            return new Decision(DecisionCode.VALIDATION_ERROR, false, "Tool name is blank", mode);
        }

        if (policy.devYoloOverride()) {
            return new Decision(DecisionCode.OVERRIDE_ALLOWED, true, "Allowed by yolo override", mode);
        }

        Set<String> allowedByAgent = agentToolIds == null ? Set.of() : Set.copyOf(agentToolIds);
        if (!allowedByAgent.contains(toolName)) {
            return new Decision(DecisionCode.DENIED, false, "Denied: tool not allowed by agent settings", mode);
        }

        if (policy.deniedTools().contains(toolName)) {
            return new Decision(DecisionCode.DENIED, false, "Denied: tool is blacklisted", mode);
        }

        Decision modeDecision = switch (mode) {
            case APPROVE_ALL -> new Decision(DecisionCode.ALLOWED, true, "Allowed by approve-all mode", mode);
            case DENY_ALL -> new Decision(DecisionCode.DENIED, false, "Denied by deny-all mode", mode);
            case BLACKLIST -> new Decision(DecisionCode.ALLOWED, true, "Allowed by blacklist mode", mode);
            case WHITELIST -> authorizeWhitelist(toolName, policy, mode);
            case PROMPT -> authorizePrompt(request, mode, "Approval required by prompt mode");
        };
        if (!modeDecision.allowed()) {
            return modeDecision;
        }

        Decision pathDecision = validatePathPolicy(toolName, request.toolCall().argumentsJson(), policy, mode);
        if (pathDecision != null) {
            return pathDecision;
        }

        Decision commandDecision = validateCommandPolicy(request, toolName, request.toolCall().argumentsJson(), policy, mode);
        if (commandDecision != null) {
            return commandDecision;
        }

        Decision webDecision = validateWebPolicy(toolName, request.toolCall().argumentsJson(), policy, mode);
        if (webDecision != null) {
            return webDecision;
        }

        return modeDecision;
    }

    private Decision authorizeWhitelist(String toolName, ToolPolicy policy, RuntimeConfig.SecurityMode mode) {
        if (policy.allowedTools().isEmpty()) {
            return new Decision(DecisionCode.ALLOWED, true, "Allowed by agent gate (whitelist empty)", mode);
        }
        if (policy.allowedTools().contains(toolName)) {
            return new Decision(DecisionCode.ALLOWED, true, "Allowed by whitelist", mode);
        }
        return new Decision(DecisionCode.DENIED, false, "Denied: tool not in whitelist", mode);
    }

    private Decision authorizePrompt(ToolRequest request, RuntimeConfig.SecurityMode mode, String reason) {
        if (approvalCallback == null) {
            return new Decision(DecisionCode.DENIED, false, "Denied: prompt required but no approval callback is configured", mode);
        }

        try {
            ApprovalResponse response = approvalCallback.approve(new ApprovalRequest(
                    request.sessionId(),
                    request.agentId(),
                    request.toolCall().name(),
                    request.toolCall().argumentsJson(),
                    reason
            ));
            if (response == ApprovalResponse.APPROVE) {
                return new Decision(DecisionCode.ALLOWED, true, "Allowed by approval callback", mode);
            }
            return new Decision(DecisionCode.DENIED, false, "Denied by approval callback", mode);
        } catch (Throwable ignored) {
            return new Decision(DecisionCode.DENIED, false, "Denied: approval callback failed", mode);
        }
    }

    private Decision validatePathPolicy(String toolName, String argsJson, ToolPolicy policy, RuntimeConfig.SecurityMode mode) {
        if (!FILE_TOOLS.contains(toolName)) {
            return null;
        }
        if (policy.allowedPaths().isEmpty()) {
            return null;
        }

        List<String> paths = extractStringPaths(argsJson, List.of("path", "filePath", "targetPath", "fromPath", "toPath", "rootPath"));
        if (paths.isEmpty()) {
            return null;
        }

        List<Path> roots;
        try {
            roots = policy.allowedPaths().stream().map(this::normalizeToAbsolute).toList();
        } catch (InvalidPathException ignored) {
            return new Decision(DecisionCode.VALIDATION_ERROR, false, "Invalid allowedPaths configuration", mode);
        }

        for (String pathValue : paths) {
            Path candidate;
            try {
                candidate = normalizeToAbsolute(pathValue);
            } catch (InvalidPathException ignored) {
                return new Decision(DecisionCode.VALIDATION_ERROR, false, "Invalid tool path: " + pathValue, mode);
            }

            boolean allowed = roots.stream().anyMatch(candidate::startsWith);
            if (!allowed) {
                return new Decision(DecisionCode.DENIED, false, "Denied: path outside allowed roots", mode);
            }
        }

        return null;
    }

    private Decision validateCommandPolicy(
            ToolRequest request,
            String toolName,
            String argsJson,
            ToolPolicy policy,
            RuntimeConfig.SecurityMode mode
    ) {
        if (!"shell_command".equals(toolName)) {
            return null;
        }

        List<String> commandTokens = extractCommandTokens(argsJson);
        if (commandTokens.isEmpty()) {
            return new Decision(DecisionCode.VALIDATION_ERROR, false, "Missing shell command", mode);
        }

        for (CommandRule rule : policy.commandRules()) {
            if (rule.commandPrefix().isEmpty()) {
                continue;
            }
            if (!startsWithPrefix(commandTokens, rule.commandPrefix())) {
                continue;
            }

            Decision ruleDecision = switch (rule.action()) {
                case ALLOW -> null;
                case DENY -> new Decision(DecisionCode.DENIED, false, "Denied by command rule: " + rule.id(), mode);
                case PROMPT -> authorizePrompt(request, mode, "Approval required by command rule: " + rule.id());
            };

            if (ruleDecision != null) {
                return ruleDecision;
            }
        }

        if (!policy.allowedCommands().isEmpty()) {
            String command = commandTokens.getFirst();
            if (!policy.allowedCommands().contains(command)) {
                return new Decision(DecisionCode.DENIED, false, "Denied: command not in allowedCommands", mode);
            }
        }

        return null;
    }

    private Decision validateWebPolicy(String toolName, String argsJson, ToolPolicy policy, RuntimeConfig.SecurityMode mode) {
        if (!toolName.startsWith("web_")) {
            return null;
        }

        String url = extractFirstString(argsJson, List.of("url", "targetUrl"));
        if (url == null || url.isBlank()) {
            return new Decision(DecisionCode.DENIED, false, "Denied: web tool requires explicit URL", mode);
        }

        boolean isLocal = isLocalUrl(url);
        if (isLocal && !policy.webAccess().localEnabled()) {
            return new Decision(DecisionCode.DENIED, false, "Denied: local web access disabled", mode);
        }
        if (!isLocal && !policy.webAccess().externalEnabled()) {
            return new Decision(DecisionCode.DENIED, false, "Denied: external web access disabled", mode);
        }

        return null;
    }

    private boolean isLocalUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean startsWithPrefix(List<String> command, List<String> prefix) {
        if (prefix.size() > command.size()) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (!Objects.equals(command.get(i), prefix.get(i))) {
                return false;
            }
        }
        return true;
    }

    private List<String> extractStringPaths(String argsJson, List<String> keys) {
        JsonNode json = readArgsJson(argsJson);
        if (json == null || !json.isObject()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (String key : keys) {
            JsonNode value = json.get(key);
            if (value == null) {
                continue;
            }
            if (value.isTextual()) {
                values.add(value.asText());
            } else if (value.isArray()) {
                value.forEach(node -> {
                    if (node.isTextual()) {
                        values.add(node.asText());
                    }
                });
            }
        }
        return values;
    }

    private List<String> extractCommandTokens(String argsJson) {
        String cmd = extractFirstString(argsJson, List.of("cmd", "command"));
        if (cmd == null || cmd.isBlank()) {
            return List.of();
        }
        return List.of(cmd.trim().split("\\s+"));
    }

    private String extractFirstString(String argsJson, List<String> keys) {
        JsonNode json = readArgsJson(argsJson);
        if (json == null || !json.isObject()) {
            return null;
        }

        for (String key : keys) {
            JsonNode value = json.get(key);
            if (value != null && value.isTextual()) {
                return value.asText();
            }
        }
        return null;
    }

    private JsonNode readArgsJson(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(argsJson);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Path normalizeToAbsolute(String pathText) {
        Path path = Path.of(pathText);
        if (!path.isAbsolute()) {
            path = workspaceRoot.resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    public SecurityEvent toEvent(ToolRequest request, Decision decision) {
        return new SecurityEvent(
                request.sessionId(),
                request.agentId(),
                request.toolCall().name(),
                decision.code(),
                decision.allowed(),
                decision.reason(),
                decision.mode()
        );
    }

    @FunctionalInterface
    public interface ApprovalCallback {
        ApprovalResponse approve(ApprovalRequest request);
    }

    public enum ApprovalResponse {
        APPROVE,
        DENY
    }

    public enum DecisionCode {
        ALLOWED,
        DENIED,
        VALIDATION_ERROR,
        OVERRIDE_ALLOWED
    }

    public record Decision(
            DecisionCode code,
            boolean allowed,
            String reason,
            RuntimeConfig.SecurityMode mode
    ) {
        public Decision {
            code = code == null ? DecisionCode.DENIED : code;
            reason = reason == null ? "" : reason;
            mode = mode == null ? RuntimeConfig.SecurityMode.BLACKLIST : mode;
        }
    }

    public record SecurityEvent(
            String sessionId,
            String agentId,
            String toolName,
            DecisionCode decisionCode,
            boolean allowed,
            String reason,
            RuntimeConfig.SecurityMode mode
    ) {
        public SecurityEvent {
            sessionId = sessionId == null ? "" : sessionId;
            agentId = agentId == null ? "" : agentId;
            toolName = toolName == null ? "" : toolName;
            decisionCode = decisionCode == null ? DecisionCode.DENIED : decisionCode;
            reason = reason == null ? "" : reason;
            mode = mode == null ? RuntimeConfig.SecurityMode.BLACKLIST : mode;
        }
    }

    public record ApprovalRequest(
            String sessionId,
            String agentId,
            String toolName,
            String argumentsJson,
            String reason
    ) {
        public ApprovalRequest {
            sessionId = sessionId == null ? "" : sessionId;
            agentId = agentId == null ? "" : agentId;
            toolName = toolName == null ? "" : toolName;
            argumentsJson = argumentsJson == null ? "" : argumentsJson;
            reason = reason == null ? "" : reason;
        }
    }

    public record ToolPolicy(
            RuntimeConfig.SecurityMode mode,
            boolean devYoloOverride,
            Set<String> allowedTools,
            Set<String> deniedTools,
            List<String> allowedPaths,
            Set<String> allowedCommands,
            WebAccessPolicy webAccess,
            List<CommandRule> commandRules
    ) {
        public ToolPolicy {
            mode = mode == null ? RuntimeConfig.SecurityMode.BLACKLIST : mode;
            allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
            deniedTools = deniedTools == null ? Set.of() : Set.copyOf(deniedTools);
            allowedPaths = allowedPaths == null ? List.of() : List.copyOf(allowedPaths);
            allowedCommands = allowedCommands == null ? Set.of() : Set.copyOf(allowedCommands);
            webAccess = webAccess == null ? new WebAccessPolicy(false, false) : webAccess;
            commandRules = commandRules == null ? List.of() : List.copyOf(commandRules);
        }

        public static ToolPolicy from(RuntimeConfig.SecurityPolicyConfig config) {
            List<CommandRule> rules = config.rules().stream()
                    .map(rule -> new CommandRule(rule.id(), rule.action(), rule.commandPrefix(), rule.reason()))
                    .toList();
            return new ToolPolicy(
                    config.mode(),
                    config.devYoloOverride(),
                    Set.copyOf(config.allowedTools()),
                    Set.copyOf(config.deniedTools()),
                    config.allowedPaths(),
                    Set.copyOf(config.allowedCommands()),
                    new WebAccessPolicy(config.webAccess().localEnabled(), config.webAccess().externalEnabled()),
                    rules
            );
        }
    }

    public record WebAccessPolicy(
            boolean localEnabled,
            boolean externalEnabled
    ) {
    }

    public record CommandRule(
            String id,
            RuntimeConfig.SecurityRuleAction action,
            List<String> commandPrefix,
            String reason
    ) {
        public CommandRule {
            id = id == null ? "rule" : id;
            action = action == null ? RuntimeConfig.SecurityRuleAction.DENY : action;
            commandPrefix = commandPrefix == null ? List.of() : List.copyOf(commandPrefix);
            reason = reason == null ? "" : reason;
        }
    }
}
