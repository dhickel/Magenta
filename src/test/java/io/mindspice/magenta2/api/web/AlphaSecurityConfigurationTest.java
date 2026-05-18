package io.mindspice.magenta2.api.web;

import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettings;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsService;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RuntimeSettingsController.class)
@Import({
    AlphaSecurityConfiguration.class,
    AlphaSecurityConfigurationTest.SecurityTestBeans.class,
    AlphaSecurityConfigurationTest.BrowserMutationRoute.class
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
    "magenta.alpha-access.username=alpha",
    "magenta.alpha-access.password=test-alpha-password",
    "magenta.features.schedules-enabled=false",
    "magenta.features.reactions-enabled=false"
})
class AlphaSecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Order(1)
    void readOnlyRoutesRemainPublicAndIssueCsrfCookie() throws Exception {
        mockMvc.perform(get("/api/settings/runtime"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    @Order(2)
    void mutationWithCsrfButWithoutAlphaCredentialIsRejected() throws Exception {
        mockMvc.perform(put("/api/settings/runtime")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(settingsJson()))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", "Basic realm=\"Magenta Alpha\""));
    }

    @Test
    @Order(3)
    void authenticatedMutationWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(put("/api/settings/runtime")
                .with(httpBasic("alpha", "test-alpha-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(settingsJson()))
            .andExpect(status().isForbidden())
            .andExpect(content().string(containsString("CSRF token missing or invalid")));
    }

    @Test
    @Order(4)
    void authenticatedConfiguredAlphaMutationWithCsrfSucceeds() throws Exception {
        mockMvc.perform(put("/api/settings/runtime")
                .with(httpBasic("alpha", "test-alpha-password"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(settingsJson()))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"defaultModel\":\"qwen3\"")));
    }

    @Test
    @Order(5)
    void htmxMutationWithoutCsrfGetsFragmentFriendlyForbidden() throws Exception {
        mockMvc.perform(post("/agents/_detail/agent-1/exec")
                .with(httpBasic("alpha", "test-alpha-password"))
                .header("HX-Request", "true")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("command", "pwd"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(containsString("mag-auth-error")))
            .andExpect(content().string(containsString("CSRF token missing or invalid")));
    }

    private static RuntimeSettings settings() {
        return new RuntimeSettings(
            "agent-1", "Default Agent", "qwen3", "qwen3", "qwen3", "qwen3",
            10, "qwen3", null, null, 10, true, -1
        );
    }

    private static String settingsJson() {
        return """
            {
              "defaultAgentId": "agent-1",
              "defaultAgentName": "Default Agent",
              "defaultModel": "qwen3",
              "planningModel": "qwen3",
              "summaryModel": "qwen3",
              "compactionModel": "qwen3",
              "contextBufferPercent": 10,
              "systemChatModel": "qwen3",
              "systemChatContextLimit": 10,
              "systemChatEnabled": true,
              "assignmentHistoryAutoPurgeDays": -1
            }
            """;
    }

    @TestConfiguration
    static class SecurityTestBeans {
        @Bean
        RuntimeSettingsService runtimeSettingsService() {
            return new RuntimeSettingsService(null, null, null) {
                @Override
                public RuntimeSettings get() {
                    return settings();
                }

                @Override
                public RuntimeSettings save(RuntimeSettings settings) {
                    return AlphaSecurityConfigurationTest.settings();
                }
            };
        }
    }

    @Controller
    static class BrowserMutationRoute {
        @PostMapping("/agents/_detail/{agentId}/exec")
        @ResponseBody
        String exec(@PathVariable String agentId) {
            return "executed " + agentId;
        }
    }
}
