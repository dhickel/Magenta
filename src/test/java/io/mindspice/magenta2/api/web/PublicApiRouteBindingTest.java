package io.mindspice.magenta2.api.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "magenta.alpha-access.username=alpha",
    "magenta.alpha-access.password=test-alpha-password",
    "magenta.plan.execution-stream-timeout-seconds=0"
})
class PublicApiRouteBindingTest {
    private static final Path DB_PATH = Path.of(
        System.getProperty("java.io.tmpdir"),
        "magenta-public-api-route-binding-" + UUID.randomUUID() + ".db"
    );
    private static final Path DATA_ROOT = Path.of(
        System.getProperty("java.io.tmpdir"),
        "magenta-public-api-route-binding-root-" + UUID.randomUUID()
    );
    private static final Path AI_CONFIG_PATH = DATA_ROOT.resolve("ai-config.json");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void sqliteProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH + "?foreign_keys=true");
        registry.add("app.ai.config-path", () -> aiConfigPath().toString());
    }

    @Test
    void chatRoutesBindWithoutCallingModelBackedExecution() throws Exception {
        mockMvc.perform(get("/api/chat/sessions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationIds").isArray())
            .andExpect(jsonPath("$.sessions").isArray());

        String conversationId = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/chat/" + conversationId + "/plan/execute")
                .with(alphaAuth())
                .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("Direct plan execution is disabled")));

        mockMvc.perform(post("/api/chat/" + conversationId + "/plan/execute/stream")
                .with(alphaAuth())
                .with(csrf())
                .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isBadRequest());
    }

    @Test
    void planRoutesBindDtosAndSubmittedSseEvent() throws Exception {
        String agentId = createAgent();
        String planId = createPlan("Route Plan");

        mockMvc.perform(get("/api/plans"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(planId)))
            .andExpect(content().string(containsString("Route Plan")));

        mockMvc.perform(get("/api/plans/" + planId + "/chat-prompt"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.prompt", containsString("Route Plan")));

        MvcResult stream = mockMvc.perform(post("/api/plans/" + planId + "/runs/stream")
                .with(alphaAuth())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content(json(Map.of("agentId", agentId))))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(stream))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", containsString(MediaType.TEXT_EVENT_STREAM_VALUE)))
            .andExpect(content().string(containsString("event:submitted")))
            .andExpect(content().string(containsString("\"event\":\"submitted\"")))
            .andExpect(content().string(containsString("\"taskId\":\"" + planId + "\"")))
            .andExpect(content().string(containsString("\"priority\":9")));
    }

    @Test
    void taskRoutesBindDtosAndSubmittedSseEvent() throws Exception {
        String agentId = createAgent();
        String taskId = createTask("Route Task");

        mockMvc.perform(get("/api/tasks"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(taskId)))
            .andExpect(content().string(containsString("Route Task")));

        MvcResult stream = mockMvc.perform(post("/api/tasks/" + taskId + "/runs/stream")
                .with(alphaAuth())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content(json(Map.of("agentId", agentId))))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(stream))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("event:submitted")))
            .andExpect(content().string(containsString("\"event\":\"submitted\"")))
            .andExpect(content().string(containsString("\"taskId\":\"" + taskId + "\"")));
    }

    @Test
    void workflowRoutesBindDtosAndFailureSseEvent() throws Exception {
        String workflowId = createWorkflow("Route Workflow");

        mockMvc.perform(get("/api/workflows"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(workflowId)))
            .andExpect(content().string(containsString("Route Workflow")));

        mockMvc.perform(post("/api/workflows/" + workflowId + "/runs")
                .with(alphaAuth())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("agentId", createAgent()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        MvcResult stream = mockMvc.perform(post("/api/workflows/" + workflowId + "/runs/stream")
                .with(alphaAuth())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content(json(Map.of("agentId", createAgent()))))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(stream))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("event:failed")))
            .andExpect(content().string(containsString("\"event\":\"failed\"")));
    }

    @Test
    void jobProjectAgentOutputAndRuntimeRoutesBindDtos() throws Exception {
        String agentId = createAgent();
        String projectId = createProject(agentId);
        String jobId = createJob(agentId, projectId);

        mockMvc.perform(get("/api/jobs"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(jobId)))
            .andExpect(content().string(containsString("Route Job")));

        mockMvc.perform(post("/api/jobs/" + jobId + "/runs")
                .with(alphaAuth())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("agentId", agentId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assignmentType").value("JOB_RUN"))
            .andExpect(jsonPath("$.priority").value(9));

        mockMvc.perform(get("/api/projects"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(projectId)))
            .andExpect(content().string(containsString("Route Project")));

        mockMvc.perform(get("/api/agents"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(agentId)))
            .andExpect(content().string(containsString("ACTIVE")));

        mockMvc.perform(get("/api/agents/" + agentId + "/workspace"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ownerType").value("AGENT"))
            .andExpect(jsonPath("$.ownerId").value(agentId));

        mockMvc.perform(get("/api/outputs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/runtime/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").isBoolean())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.checkedAt").exists());

        mockMvc.perform(get("/api/settings/runtime"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.defaultModel").exists());

        mockMvc.perform(put("/api/settings/runtime")
                .with(alphaAuth())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(runtimeSettings(agentId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.defaultAgentId").value(agentId))
            .andExpect(jsonPath("$.defaultModel").value("local-qwen"));
    }

    private String createAgent() throws Exception {
        String agentId = "route-agent-" + UUID.randomUUID();
        String agentName = "Route Agent " + agentId;
        mockMvc.perform(post("/api/agents")
                .with(alphaAuth())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                    "id", agentId,
                    "name", agentName,
                    "status", "ACTIVE",
                    "defaultModel", "local-qwen",
                    "directLineEnabled", true
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(agentId));
        return agentId;
    }

    private String createPlan(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/plans")
                .with(alphaAuth())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                    "title", title,
                    "summary", "Route summary",
                    "goal", "Route goal",
                    "deliverables", java.util.List.of("Route deliverable"),
                    "validationCriteria", java.util.List.of("Route criterion"),
                    "planningModel", "local-qwen",
                    "executionModel", "local-qwen"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();
        return read(result, "id");
    }

    private String createTask(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks")
                .with(alphaAuth())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                    "title", title,
                    "summary", "Route summary",
                    "goal", "Route goal",
                    "validationCriteria", java.util.List.of("Route criterion")
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();
        return read(result, "id");
    }

    private String createWorkflow(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/workflows")
                .with(alphaAuth())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                    "title", title,
                    "summary", "Route summary",
                    "nodes", java.util.List.of(),
                    "routes", java.util.List.of()
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();
        return read(result, "id");
    }

    private String createProject(String agentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                .with(alphaAuth())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                    "name", "Route Project",
                    "description", "Route summary",
                    "ownerAgentId", agentId
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();
        return read(result, "id");
    }

    private String createJob(String agentId, String projectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/jobs")
                .with(alphaAuth())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                    "ownerAgentId", agentId,
                    "projectId", projectId,
                    "status", "DRAFT",
                    "title", "Route Job",
                    "summary", "Route summary",
                    "items", java.util.List.of(),
                    "model", "local-qwen"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();
        return read(result, "id");
    }

    private Map<String, Object> runtimeSettings(String agentId) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("defaultAgentId", agentId);
        settings.put("defaultAgentName", "Route Agent");
        settings.put("defaultModel", "local-qwen");
        settings.put("planningModel", "local-qwen");
        settings.put("summaryModel", "local-qwen");
        settings.put("compactionModel", "local-qwen");
        settings.put("contextBufferPercent", 10);
        settings.put("systemChatModel", "local-qwen");
        settings.put("systemChatContextLimit", 100);
        settings.put("systemChatEnabled", true);
        settings.put("assignmentHistoryAutoPurgeDays", -1);
        return settings;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String read(MvcResult result, String field) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return node.get(field).asText();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor alphaAuth() {
        return httpBasic("alpha", "test-alpha-password");
    }

    private static Path aiConfigPath() {
        try {
            Files.createDirectories(DATA_ROOT);
            Files.createDirectories(DATA_ROOT.resolve("prompts"));
            Files.writeString(DATA_ROOT.resolve("prompts/system.md"), "Route binding test agent.");
            Files.writeString(AI_CONFIG_PATH, """
                {
                  "defaultAgent": "magenta",
                  "defaultModel": "local-qwen",
                  "summeryModel": "local-qwen",
                  "planningModel": "local-qwen",
                  "compactionModel": "local-qwen",
                  "contextBufferPercent": 33,
                  "unsafeAllowWildcardShellCommands": false,
                  "dataRoot": "%s",
                  "models": {
                    "local-qwen": {
                      "remoteModelName": "qwen3.6:35b",
                      "remoteEndpoint": "http://127.0.0.1:11434",
                      "endpointType": "OLLAMA",
                      "contextLength": 32000,
                      "thinkLevel": 0
                    }
                  },
                  "agents": {
                    "magenta": {
                      "model": "local-qwen",
                      "systemPrompt": "prompts/system.md",
                      "approvedTools": [],
                      "allowedShellCommands": []
                    }
                  }
                }
                """.formatted(DATA_ROOT.toAbsolutePath()));
            return AI_CONFIG_PATH;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create route-binding AI config", exception);
        }
    }
}
