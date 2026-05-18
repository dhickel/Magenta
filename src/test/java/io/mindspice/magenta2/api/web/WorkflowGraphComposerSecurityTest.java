package io.mindspice.magenta2.api.web;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowGraphComposerSecurityTest {
    private static final Path WORKFLOWS_JS = Path.of("src/main/resources/static/js/orchestration/workflows.js");

    @Test
    void graphAndSidePanelUseTextSafeDomConstructionForPersistedNodeFields() throws Exception {
        String js = Files.readString(WORKFLOWS_JS);
        String renderCanvas = methodBody(js, "renderCanvas");
        String renderSidePanel = methodBody(js, "renderSidePanel");
        String scriptLikePayload = "<script>alert('workflow-xss')</script><img src=x onerror=alert(1)>";

        assertThat(scriptLikePayload).contains("<script>").contains("onerror");
        assertThat(renderCanvas)
            .doesNotContain("innerHTML")
            .contains("textElement(\"strong\", node.label || node.key)")
            .contains("textElement(\"div\", node.type)")
            .contains("textElement(\"div\", node.key, \"graph-node-key\")");
        assertThat(renderSidePanel)
            .doesNotContain("innerHTML")
            .contains("labeledInput(\"Key\", \"node-key\", node.key)")
            .contains("labeledInput(\"Label\", \"node-label\", node.label || \"\")")
            .contains("labeledInput(\"Type\", \"node-type\", node.type, true)")
            .contains("labeledInput(\"Task Plan ID\", \"node-plan-id\", node.planId || \"\")")
            .contains("labeledTextarea(\"Message Template\", \"node-message\", node.messageTemplate || \"\")")
            .contains("labeledTextarea(\"Config JSON\", \"node-config\", asJsonText(node.config || {}))")
            .contains("labeledTextarea(\"Input Ports JSON\", \"node-input-ports\", asJsonText(node.inputPorts || []))")
            .contains("labeledTextarea(\"Output Ports JSON\", \"node-output-ports\", asJsonText(node.outputPorts || []))");
        assertThat(js)
            .doesNotContain("<strong>${node")
            .doesNotContain("<div>${node")
            .doesNotContain("value=\"${node")
            .doesNotContain("<textarea id=\"node-message\">${node")
            .doesNotContain("<textarea id=\"node-config\">${asJsonText")
            .doesNotContain("<textarea id=\"node-input-ports\">${asJsonText")
            .doesNotContain("<textarea id=\"node-output-ports\">${asJsonText");
    }

    private static String methodBody(String source, String methodName) {
        int methodStart = source.indexOf("\n    " + methodName + "() {");
        assertThat(methodStart).as("method %s should exist", methodName).isGreaterThanOrEqualTo(0);
        int openBrace = source.indexOf('{', methodStart);
        assertThat(openBrace).as("method %s should have a body", methodName).isGreaterThanOrEqualTo(0);

        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, i);
                }
            }
        }
        throw new AssertionError("Could not find end of method body for " + methodName);
    }
}
