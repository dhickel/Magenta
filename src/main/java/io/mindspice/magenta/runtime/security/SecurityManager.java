package io.mindspice.magenta.runtime.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.tools.ToolRequest;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SecurityManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolPolicy defaultPolicy;
    private final ApprovalCallback approvalCallback;
    private final Path workspaceRoot;
    private final Path workspaceRootReal;
    private final Map<String, ToolSecurityDescriptor> securityDescriptors;
    private final ConcurrentMap<UUID, ToolPolicy> sessionPolicies = new ConcurrentHashMap<>();

    public SecurityManager(
            RuntimeConfig.SecurityPolicyConfig securityConfig,
            Path workspaceRoot,
            ApprovalCallback approvalCallback
    ) {
        this(securityConfig, workspaceRoot, approvalCallback, Map.of());
    }

    public SecurityManager(
            RuntimeConfig.SecurityPolicyConfig securityConfig,
            Path workspaceRoot,
            ApprovalCallback approvalCallback,
            Map<String, ToolSecurityDescriptor> securityDescriptors
    ) {
        this.defaultPolicy = ToolPolicy.from(securityConfig == null
                ? RuntimeConfig.SecurityPolicyConfig.defaults()
                : securityConfig);
        this.workspaceRoot = workspaceRoot == null
                ? Path.of("").toAbsolutePath().normalize()
                : workspaceRoot.toAbsolutePath().normalize();
        this.workspaceRootReal = resolveRealWorkspaceRoot(this.workspaceRoot);
        this.approvalCallback = approvalCallback;
        this.securityDescriptors = securityDescriptors == null ? Map.of() : Map.copyOf(securityDescriptors);
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

        String policyToolName = policyToolName(toolName);

        if (policy.deniedTools().contains(toolName) || policy.deniedTools().contains(policyToolName)) {
            return new Decision(DecisionCode.DENIED, false, "Denied: tool is blacklisted", mode);
        }

        Decision modeDecision = switch (mode) {
            case APPROVE_ALL -> new Decision(DecisionCode.ALLOWED, true, "Allowed by approve-all mode", mode);
            case DENY_ALL -> new Decision(DecisionCode.DENIED, false, "Denied by deny-all mode", mode);
            case BLACKLIST -> new Decision(DecisionCode.ALLOWED, true, "Allowed by blacklist mode", mode);
            case WHITELIST -> authorizeWhitelist(toolName, policyToolName, policy, mode);
            case PROMPT -> authorizePrompt(request, mode, "Approval required by prompt mode");
        };
        if (!modeDecision.allowed()) {
            return modeDecision;
        }

        ToolSecurityDescriptor descriptor = securityDescriptors.get(toolName);

        Decision pathDecision = validatePathPolicy(request, toolName, request.toolCall().argumentsJson(), policy, mode, descriptor);
        if (pathDecision != null) {
            return pathDecision;
        }

        Decision commandDecision = validateCommandPolicy(request, toolName, request.toolCall().argumentsJson(), policy, mode, descriptor);
        if (commandDecision != null) {
            return commandDecision;
        }

        Decision webDecision = validateWebPolicy(toolName, request.toolCall().argumentsJson(), policy, mode, descriptor);
        if (webDecision != null) {
            return webDecision;
        }

        Decision customDecision = validateCustomPolicy(request, policy, mode, descriptor);
        if (customDecision != null) {
            return customDecision;
        }

        return modeDecision;
    }

    private Decision authorizeWhitelist(String toolName, String policyToolName, ToolPolicy policy, RuntimeConfig.SecurityMode mode) {
        if (policy.allowedTools().isEmpty()) {
            return new Decision(DecisionCode.ALLOWED, true, "Allowed by agent gate (whitelist empty)", mode);
        }
        if (policy.allowedTools().contains(toolName) || policy.allowedTools().contains(policyToolName)) {
            return new Decision(DecisionCode.ALLOWED, true, "Allowed by whitelist", mode);
        }
        return new Decision(DecisionCode.DENIED, false, "Denied: tool not in whitelist", mode);
    }

    private String policyToolName(String toolName) {
        if (toolName != null && toolName.startsWith("todo_")) {
            return "todo";
        }
        return toolName == null ? "" : toolName;
    }

    private Decision authorizePrompt(ToolRequest request, RuntimeConfig.SecurityMode mode, String reason) {
        if (approvalCallback == null) {
            return new Decision(
                    DecisionCode.DENIED,
                    false,
                    "Denied: " + reason + " (no approval callback is configured)",
                    mode
            );
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
            return new Decision(DecisionCode.DENIED, false, "Denied by approval callback: " + reason, mode);
        } catch (Throwable ignored) {
            return new Decision(DecisionCode.DENIED, false, "Denied: approval callback failed for: " + reason, mode);
        }
    }

    private Decision validatePathPolicy(
            ToolRequest request,
            String toolName,
            String argsJson,
            ToolPolicy policy,
            RuntimeConfig.SecurityMode mode,
            ToolSecurityDescriptor descriptor
    ) {
        if (descriptor == null || (!descriptor.requiresPath() && descriptor.pathKeys().isEmpty())) {
            return null;
        }

        List<String> paths = extractStringValues(argsJson, descriptor.pathKeys());
        if (paths.isEmpty()) {
            if (!descriptor.defaultPathWhenMissing().isBlank()) {
                paths = List.of(descriptor.defaultPathWhenMissing());
            }
        }
        if (paths.isEmpty()) {
            if (descriptor.requiresPath()) {
                return new Decision(DecisionCode.VALIDATION_ERROR, false, "Missing required tool path argument(s)", mode);
            }
            return null;
        }

        if (policy.allowedPaths().isEmpty()) {
            return null;
        }

        List<Path> approvedRoots;
        try {
            approvedRoots = resolveApprovedRoots(policy.allowedPaths());
        } catch (InvalidPathException ignored) {
            return new Decision(DecisionCode.VALIDATION_ERROR, false, "Invalid allowedPaths configuration", mode);
        }

        for (String pathValue : paths) {
            PathResolution resolution = resolvePathForPolicy(pathValue);
            if (resolution.error() != null) {
                return new Decision(DecisionCode.VALIDATION_ERROR, false, "Invalid tool path: " + pathValue, mode);
            }

            Path candidate = resolution.resolvedPath();
            boolean approved = approvedRoots.stream().anyMatch(candidate::startsWith);
            if (!approved) {
                Decision promptDecision = authorizePrompt(
                        request,
                        mode,
                        "Approval required: path outside approved roots -> " + candidate
                );
                if (!promptDecision.allowed()) {
                    return promptDecision;
                }
            }
        }

        return null;
    }

    private Decision validateCommandPolicy(
            ToolRequest request,
            String toolName,
            String argsJson,
            ToolPolicy policy,
            RuntimeConfig.SecurityMode mode,
            ToolSecurityDescriptor descriptor
    ) {
        if (descriptor == null || (!descriptor.requiresCommand() && descriptor.commandKeys().isEmpty())) {
            return null;
        }

        String command = extractFirstString(argsJson, descriptor.commandKeys());
        if (command == null || command.isBlank()) {
            if (descriptor.requiresCommand()) {
                return new Decision(DecisionCode.VALIDATION_ERROR, false, "Missing shell command", mode);
            }
            return null;
        }

        CommandParse parse = parseCommand(command);
        if (parse.error() != null) {
            return new Decision(DecisionCode.VALIDATION_ERROR, false, parse.error(), mode);
        }
        if (!parse.operators().isEmpty()) {
            String operators = String.join(", ", parse.operators().stream().distinct().toList());
            return new Decision(
                    DecisionCode.VALIDATION_ERROR,
                    false,
                    "Shell operators are not allowed by security policy: " + operators,
                    mode
            );
        }

        List<String> commandTokens = parse.tokens();
        if (commandTokens.isEmpty()) {
            return new Decision(DecisionCode.VALIDATION_ERROR, false, "Missing shell command", mode);
        }

        for (CommandRule rule : policy.commandRules()) {
            List<String> prefix = rule.commandPrefix();
            if (!prefix.isEmpty() && !startsWithPrefix(commandTokens, prefix)) {
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
            String executable = commandTokens.getFirst();
            if (!policy.allowedCommands().contains(executable)) {
                return new Decision(DecisionCode.DENIED, false, "Denied: command not in allowedCommands", mode);
            }
        }

        return null;
    }

    private Decision validateWebPolicy(
            String toolName,
            String argsJson,
            ToolPolicy policy,
            RuntimeConfig.SecurityMode mode,
            ToolSecurityDescriptor descriptor
    ) {
        boolean isWebTool = toolName.startsWith("web_");
        if (!isWebTool && (descriptor == null || (!descriptor.requiresUrl() && descriptor.urlKeys().isEmpty()))) {
            return null;
        }

        List<String> urlKeys = descriptor == null || descriptor.urlKeys().isEmpty()
                ? List.of("url", "targetUrl")
                : descriptor.urlKeys();
        List<String> urls = extractStringValues(argsJson, urlKeys);
        if (urls.isEmpty()) {
            if (isWebTool || (descriptor != null && descriptor.requiresUrl())) {
                return new Decision(DecisionCode.VALIDATION_ERROR, false, "Missing required URL argument", mode);
            }
            return null;
        }

        for (String url : urls) {
            boolean isLocal = isLocalUrl(url);
            if (isLocal && !policy.webAccess().localEnabled()) {
                return new Decision(DecisionCode.DENIED, false, "Denied: local web access disabled", mode);
            }
            if (!isLocal && !policy.webAccess().externalEnabled()) {
                return new Decision(DecisionCode.DENIED, false, "Denied: external web access disabled", mode);
            }
        }

        return null;
    }

    private Decision validateCustomPolicy(
            ToolRequest request,
            ToolPolicy policy,
            RuntimeConfig.SecurityMode mode,
            ToolSecurityDescriptor descriptor
    ) {
        if (descriptor == null || descriptor.validator() == null) {
            return null;
        }
        try {
            return descriptor.validator().validate(
                    new ToolSecurityDescriptor.ValidationContext(request, policy, mode, workspaceRoot, workspaceRootReal)
            );
        } catch (Exception ignored) {
            return new Decision(DecisionCode.VALIDATION_ERROR, false, "Security validator callback failed", mode);
        }
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

    private List<String> extractStringValues(String argsJson, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
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

    private Path resolveRealWorkspaceRoot(Path workspaceRoot) {
        try {
            if (workspaceRoot != null && Files.exists(workspaceRoot, LinkOption.NOFOLLOW_LINKS)) {
                return workspaceRoot.toRealPath();
            }
        } catch (Exception ignored) {
            // best effort
        }
        return workspaceRoot;
    }

    private List<Path> resolveApprovedRoots(List<String> allowedRoots) {
        if (allowedRoots == null || allowedRoots.isEmpty()) {
            return List.of();
        }
        List<Path> roots = new ArrayList<>();
        for (String root : allowedRoots) {
            if (root == null || root.isBlank()) {
                continue;
            }
            Path absolute = normalizeToAbsolute(root.trim());
            roots.add(resolveApprovedRoot(absolute));
        }
        return List.copyOf(roots);
    }

    private Path resolveApprovedRoot(Path absolute) {
        try {
            if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
                return absolute.toRealPath().normalize();
            }
        } catch (Exception ignored) {
            // best effort
        }
        return absolute.toAbsolutePath().normalize();
    }

    private PathResolution resolvePathForPolicy(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            return new PathResolution(null, "blank_path");
        }
        try {
            Path absolute = normalizeToAbsolute(pathText.trim());
            return new PathResolution(resolvePathWithExistingAncestor(absolute), null);
        } catch (Exception ignored) {
            return new PathResolution(null, "invalid_path");
        }
    }

    private Path resolvePathWithExistingAncestor(Path absolute) {
        Path normalized = absolute.toAbsolutePath().normalize();
        Path current = normalized;
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            current = current.getParent();
        }
        if (current == null) {
            return normalized;
        }
        try {
            Path realAncestor = current.toRealPath();
            if (Objects.equals(current, normalized)) {
                return realAncestor.normalize();
            }
            Path suffix = current.relativize(normalized);
            return realAncestor.resolve(suffix).normalize();
        } catch (Exception ignored) {
            return normalized;
        }
    }

    private CommandParse parseCommand(String command) {
        if (command == null || command.isBlank()) {
            return new CommandParse(List.of(), List.of(), "Missing shell command");
        }

        List<String> tokens = new ArrayList<>();
        List<String> operators = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaping = false;

        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);

            if (escaping) {
                current.append(c);
                escaping = false;
                continue;
            }

            if (inSingle) {
                if (c == '\'') {
                    inSingle = false;
                } else {
                    current.append(c);
                }
                continue;
            }

            if (inDouble) {
                if (c == '"') {
                    inDouble = false;
                    continue;
                }
                if (c == '\\') {
                    if (i + 1 < command.length()) {
                        char next = command.charAt(i + 1);
                        if (next == '"' || next == '\\' || next == '$' || next == '`') {
                            current.append(next);
                            i++;
                            continue;
                        }
                    }
                }
                current.append(c);
                continue;
            }

            if (c == '\\') {
                escaping = true;
                continue;
            }
            if (c == '\'') {
                inSingle = true;
                continue;
            }
            if (c == '"') {
                inDouble = true;
                continue;
            }
            if (c == '\n' || c == '\r') {
                flushToken(tokens, current);
                operators.add(String.valueOf(c));
                continue;
            }
            if (Character.isWhitespace(c)) {
                flushToken(tokens, current);
                continue;
            }

            String operator = readShellOperator(command, i);
            if (operator != null) {
                flushToken(tokens, current);
                operators.add(operator);
                i += operator.length() - 1;
                continue;
            }

            current.append(c);
        }

        if (escaping) {
            return new CommandParse(List.of(), List.of(), "Invalid shell command: trailing escape");
        }
        if (inSingle || inDouble) {
            return new CommandParse(List.of(), List.of(), "Invalid shell command: unterminated quote");
        }

        flushToken(tokens, current);
        return new CommandParse(List.copyOf(tokens), List.copyOf(operators), null);
    }

    private String readShellOperator(String command, int index) {
        char c = command.charAt(index);
        char next = index + 1 < command.length() ? command.charAt(index + 1) : '\0';

        if (c == '$' && next == '(') {
            return "$(";
        }
        if (c == ';') {
            return ";";
        }
        if (c == '|') {
            return next == '|' ? "||" : "|";
        }
        if (c == '&') {
            return next == '&' ? "&&" : "&";
        }
        if (c == '<') {
            return next == '<' ? "<<" : "<";
        }
        if (c == '>') {
            return next == '>' ? ">>" : ">";
        }
        if (c == '`') {
            return "`";
        }
        if (c == '\n' || c == '\r') {
            return String.valueOf(c);
        }
        return null;
    }

    private void flushToken(List<String> tokens, StringBuilder current) {
        if (!current.isEmpty()) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }

    private record PathResolution(Path resolvedPath, String error) {
    }

    private record CommandParse(List<String> tokens, List<String> operators, String error) {
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
