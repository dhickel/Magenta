package io.mindspice.magenta2.ai.chat.tool.shell;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class AgentShellTools {
    private final AgentShellToolService shellToolService;
    private final ObjectMapper objectMapper;

    public AgentShellTools(AgentShellToolService shellToolService, ObjectMapper objectMapper) {
        this.shellToolService = shellToolService;
        this.objectMapper = objectMapper;
    }

    @Tool(
        name = "shell_exec",
        description = "Run an allowed Linux executable with structured arguments under the configured agent data root. Use file tools for normal inspect/read/edit flows."
    )
    public String exec(
        @ToolParam(description = "Allowed executable name to run. Must be a bare command name, not a shell string or path.")
        String command,
        @ToolParam(required = false, description = "Command arguments as separate values. Shell operators such as && are not interpreted.")
        List<String> args,
        @ToolParam(required = false, description = "Data-root-relative working directory. Defaults to '.'.")
        String workingDirectory,
        @ToolParam(required = false, description = "Timeout in seconds. Defaults to 10 and is capped by the server.")
        Integer timeoutSeconds
    ) throws Exception {
        return json(shellToolService.exec(command, args, workingDirectory, timeoutSeconds));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize shell tool result", exception);
        }
    }
}
