# Avatar Mock Laws Tool Research Review

## Scope

This is a research-only follow-up to the Avatar plugin-system research. It does not implement production code, does not add dependencies, and does not create a plugin runtime.

"Mock laws tool" is interpreted here as a concrete, code-shaped example of a plugin-contributed Spring AI tool that performs simulated legal/laws lookup and issue spotting for demonstration only. The example must never be presented as real legal research, legal advice, legal citation, attorney-client communication, or a source of current law.

The reviewed target is Magenta in `/home/hickelpickle/Code/Java/magenta2` on 2026-05-22. The artifact assumes the current Avatar direction:

- Avatar user-centric data belongs in `avatar.sqlite`; runtime/chat/tool state stays in existing Magenta packages.
- Avatar should use the existing chat, agent profile, assignment, workspace, output, schedule, reaction, and tool runtime rather than a second runtime.
- Plugin runtime remains deferred research-only for the Avatar sprint.

Primary local anchors:

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistry.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/ToolAccessPolicy.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/*/*ToolConfiguration.java`
- `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/data-model.md`
- `docs/technical/security.md`
- `.internal-dev/reviews/2026-05-22-avatar-plugin-system-research.md`
- `.internal-dev/plans/avatar-dashboard-sprint/phase-04-avatar-assistant-behaviors.md`

Primary external references:

- Spring AI tool calling reference: https://docs.spring.io/spring-ai/reference/api/tools.html
- Spring AI `ToolCallbackProvider` Javadoc: https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/tool/ToolCallbackProvider.html
- Spring Boot 3.4 custom auto-configuration reference: https://docs.spring.io/spring-boot/3.4/reference/features/developing-auto-configuration.html
- Java `ServiceLoader` Javadoc: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/ServiceLoader.html
- Kawa overview/features/Java interop: https://www.gnu.org/software/kawa/index.html, https://www.gnu.org/software/kawa/Features.html, https://www.gnu.org/software/kawa/Method-operations.html
- ABA Model Rule 5.5, Unauthorized Practice of Law: https://www.americanbar.org/groups/professional_responsibility/publications/model_rules_of_professional_conduct/rule_5_5_unauthorized_practice_of_law_multijurisdictional_practice_of_law/
- ABA Model Rule 1.6, Confidentiality of Information: https://www.americanbar.org/groups/professional_responsibility/publications/model_rules_of_professional_conduct/rule_1_6_confidentiality_of_information/
- NIST AI RMF overview: https://www.nist.gov/itl/ai-risk-management-framework
- OWASP Top 10 for LLM Applications: https://owasp.org/www-project-top-10-for-large-language-model-applications/
- Congress.gov about/API entry points: https://www.congress.gov/about, https://api.congress.gov/
- GovInfo API overview: https://www.govinfo.gov/features/api
- U.S. Code/OLRC overview: https://uscode.house.gov/about_code.xhtml

## Findings

### Existing Magenta Tool Path Is The Right Extension Path

Magenta already has the correct host-side primitive for a contributed tool: Spring AI `ToolCallback` and `ToolCallbackProvider` beans. `ChatToolRegistry` receives all `ToolCallback` beans and all callback providers, flattens provider callbacks, indexes them by `ToolDefinition.name()`, and resolves only explicitly approved tool names. Existing file, web, shell, plan, saved-plan, task, and question tools all follow the same basic pattern: a small `@Tool` component plus a configuration class returning `MethodToolCallbackProvider.builder().toolObjects(...).build()`.

This means a later plugin-contributed mock laws tool should not create a second registry, second tool loop, or plugin-specific model path. If it is represented as a normal `ToolCallbackProvider`, the existing ChatService path can:

- attach callbacks through `ToolCallingChatOptions`;
- keep internal Spring AI tool execution disabled and execute through Magenta's explicit tool loop;
- record tool transcript entries;
- record audit events;
- apply agent approved-tool allowlists;
- preserve existing mode filtering behavior.

### Approval Behavior Is Explicit But Needs A Plugin Governance Rule

Current `ChatToolRegistry.resolveApprovedTools(...)` supports a wildcard `"*"` that returns every registered tool. Existing seeding code ignores a legacy wildcard during profile seeding, but the registry still honors a wildcard if it reaches it. For plugin-contributed tools, especially legal-themed tools, the governance rule should be stricter than the registry's generic capability:

- Avatar and plugin profiles should use explicit tool names only.
- No plugin tool should become visible because a profile contains `"*"`.
- Unknown approved tool names already fail fast through registry validation and should keep doing so.
- Plugin tools should not self-approve, mutate profiles, or grant adjacent tools such as `web_fetch`, `file_read`, or `shell_exec`.

`ToolAccessPolicy` currently whitelists tools in PLAN and TASK modes. A mock laws tool would not be available in those modes unless deliberately added to the mode allowlists. That is good for the sprint: the safe default is to expose legal-themed demo tools only in ordinary Avatar assistant turns when explicitly approved.

### Spring AI Supports The Needed Shape

Spring AI's tool calling API supports method tools through annotations such as `@Tool` and `@ToolParam`, `ToolCallbackProvider` for grouping tool callbacks, and runtime/default tool attachment through `ToolCallingChatOptions`. Magenta's current code aligns with this: method tools are exposed through `MethodToolCallbackProvider`, and `ChatService.toolOptions(...)` sets tool callbacks for a turn.

The mock tool should keep the model-visible annotations narrow. Tool descriptions are prompt surface. They should state:

- this is a simulated demo corpus;
- results are not real law;
- the tool cannot provide legal advice;
- users should consult a qualified legal professional for real matters.

### Java SPI Is Useful For Trusted Developer Plugins, Not End-User Safety

Java `ServiceLoader` is a reasonable discovery mechanism for trusted developer plugins. It locates providers for a well-known interface on the class path or module path. It is not a permission system, sandbox, hot-reload system, or dependency isolation system.

The safest later shape is:

- SPI discovers immutable plugin descriptors and tool contributions at startup.
- Spring Boot auto-configuration in the plugin jar contributes ordinary Spring beans.
- Magenta core still owns approval, activation, audit, and prompt policy.
- Dynamic plugin loading, runtime install/uninstall, script downloads, and untrusted third-party execution remain out of scope until a separate isolation design exists.

Spring Boot's auto-configuration reference explicitly supports external jars that publish configuration classes through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. That is a better developer-plugin packaging model than asking users to component-scan arbitrary plugin packages.

### Kawa Is Only Suitable For Trusted Local Scripting

Kawa is attractive for local scripts because it runs on the JVM, can be embedded, and provides direct Java interop. That same Java interop is the safety problem. Kawa scripts can call Java methods and access Java classes; the official docs present this as a core feature.

For Magenta, Kawa should be treated as trusted operator code only. A Kawa mock-laws script can be a useful example of how a narrow host API might feel, but it should not be treated as an in-process sandbox. If untrusted scripts are ever required, research should shift to out-of-process execution, OS-level restrictions, filesystem/network isolation, signed packages, and explicit resource quotas.

### A Legal-Themed Tool Is A High-Risk Demo Even When Mocked

The mock laws tool is more sensitive than a notes or weather demo because users may over-trust anything that looks like law. ABA Model Rule 5.5 is lawyer-focused, but it illustrates why Magenta must not hold itself out as practicing law or assisting unauthorized legal practice. ABA Rule 1.6 is also lawyer-focused, but it highlights why legal matter facts are often highly sensitive even when the tool is only a demo.

Therefore the mock laws tool should:

- use only synthetic laws, synthetic citations, and synthetic jurisdictions;
- return a disclaimer in every tool result;
- refuse to answer as if it has looked up real law;
- refuse to draft filings, contracts, pleadings, legal letters, legal strategies, or jurisdiction-specific advice;
- provide high-level educational issue spotting only;
- push real situations to qualified counsel or official public sources;
- avoid collecting names, addresses, phone numbers, case numbers, social security numbers, dates of birth, financial account numbers, immigration identifiers, health details, or other legal-matter PII.

### Real Law Lookup Is A Separate Product

If Magenta later implements real law lookup, it should use official/primary sources and source-specific freshness metadata. The relevant source family is different by law type:

- U.S. federal bills and legislative information: Congress.gov/API.
- U.S. Code: OLRC U.S. Code site and downloads.
- Official federal publications and packages: GovInfo API.
- Regulations: eCFR/Federal Register/GovInfo, depending on official/currency needs.
- State law: state legislature/court/government sources, which vary widely.

That real-law version would need citation validation, source freshness display, jurisdiction/date scoping, retrieval tests, source outage handling, legal-domain review, and a stronger product safety policy. It should not be inferred from this mock tool.

## Risk Assessment

| Risk | Severity | Why It Matters | Required Boundary |
| --- | --- | --- | --- |
| Unauthorized-practice / legal-advice confusion | High | A legal-looking assistant may be mistaken for legal advice. | Tool and final answer must say simulated, non-legal-advice, no attorney-client relationship; no specific legal recommendations. |
| Hallucinated law | High | Models may invent statutes, deadlines, rights, or procedures. | Tool returns only synthetic fixture records; no generated "citations" outside fixture ids. |
| Wrong jurisdiction or stale law | High | Legal outcomes depend on jurisdiction and date. | Demo jurisdictions only; no real-world jurisdiction claims. |
| Privacy leakage | High | Legal scenarios often include sensitive personal facts. | Do not require PII; reject or redact obvious PII; avoid storing full scenario text. |
| Prompt injection | Medium/High | User text can instruct the model/tool to ignore disclaimers or claim real-law authority. | Tool-level response always includes hard-coded disclaimers; prompt policy says not to override them. |
| Excessive agency | High | A legal tool could trigger drafting, filing, communication, or payment actions. | Read-only simulated lookup only; no external calls, no filing, no messaging, no transactions. |
| Plugin supply chain | High | Java/Kawa plugins execute with host privileges unless isolated. | Trusted developer jars only; no runtime plugin install this sprint. |
| Audit/log sensitivity | Medium/High | Tool arguments/results could persist sensitive legal facts. | Log metadata only or redacted previews; do not persist full legal scenario unless explicitly required later. |
| User overreliance | High | Users may act on a demo response despite warnings. | Keep outputs educational, short, uncertainty-forward, and professional-review-oriented. |
| Source provenance drift | Medium | If demo evolves toward real lookup, fixture/source boundaries may blur. | Name every tool and output field `mock`/`simulated`; reject real citation requests. |

## Recommendations

1. Keep this as research-only for the Avatar sprint.
2. If a demo is later approved, implement it as a first-party, read-only, synthetic-corpus tool before introducing any generic plugin runtime.
3. Require explicit tool approval: `mock_law_search`, `mock_law_read`, and `mock_law_issue_spot`; do not rely on wildcard approval.
4. Do not add mock laws tools to PLAN/TASK mode allowlists unless a separate plan justifies it.
5. Keep all mock laws methods pure/read-only: no filesystem, web, shell, database writes, messaging, document drafting, or external submission.
6. Return compact JSON with embedded disclaimer fields instead of prose-only tool output.
7. Keep model-facing descriptions and prompt policy aligned: "simulated legal education demo only."
8. Add focused tests later for tool schema, disclaimer presence, fixture-only citations, PII rejection/redaction, and registry approval.
9. If real law lookup is ever desired, treat it as a separate high-stakes retrieval feature with official-source ingestion and legal-domain safety review.
10. Do not use Kawa for this sprint; preserve it as a trusted local scripting candidate after a narrow host API exists.

## Safe Tool Contract

### Contract Summary

Tool namespace: `mock_law_*`

Status: simulated demonstration only

Capabilities:

- Search a tiny synthetic law corpus.
- Read a synthetic law entry by synthetic citation.
- Perform non-advice issue spotting against the synthetic corpus.

Non-capabilities:

- No real legal lookup.
- No legal advice.
- No attorney-client relationship.
- No drafting of legal documents.
- No form filling, filing, emailing, or submission.
- No prediction of legal outcomes.
- No real jurisdiction, statute, regulation, case, or deadline validation.
- No web, filesystem, shell, or database access.

Required disclaimer in every tool result:

```text
This is a simulated laws demo for Magenta plugin research. It is not real law,
not legal advice, and does not create an attorney-client relationship. For a
real legal issue, consult a qualified legal professional or official legal source.
```

### Tool 1: `mock_law_search`

Purpose: Return synthetic law entries that match a query or topic.

Inputs:

```json
{
  "query": "Plain-language search text. Required. Max 400 characters.",
  "demoJurisdiction": "One of: DEMO_STATE, DEMO_CITY, DEMO_FEDERAL. Optional.",
  "topic": "One of: housing, employment, privacy, consumer, procedure, general. Optional.",
  "maxResults": "Integer 1-5. Optional. Defaults to 3."
}
```

Output:

```json
{
  "notLegalAdvice": true,
  "sourceKind": "SIMULATED_CORPUS",
  "disclaimer": "This is a simulated laws demo...",
  "queryAccepted": true,
  "warnings": [],
  "results": [
    {
      "demoCitation": "DEMO-CIV-101",
      "title": "Quiet Enjoyment In Demo Residential Leases",
      "demoJurisdiction": "DEMO_STATE",
      "topic": "housing",
      "summary": "Synthetic educational summary.",
      "snippet": "Short synthetic excerpt, not real legal text."
    }
  ]
}
```

Refusal cases:

- Real citation lookup request: "I can only search synthetic demo citations."
- Specific legal advice request: "I can only provide simulated educational issue spotting."
- PII-heavy request: "Please remove personal identifiers and ask again with a generalized scenario."
- Emergency or imminent-harm request: "This demo is not appropriate; contact emergency services or a qualified professional."

### Tool 2: `mock_law_read`

Purpose: Return one synthetic law entry by fixture citation.

Inputs:

```json
{
  "demoCitation": "Synthetic citation such as DEMO-CIV-101. Required."
}
```

Output:

```json
{
  "notLegalAdvice": true,
  "sourceKind": "SIMULATED_CORPUS",
  "disclaimer": "This is a simulated laws demo...",
  "found": true,
  "entry": {
    "demoCitation": "DEMO-CIV-101",
    "title": "Quiet Enjoyment In Demo Residential Leases",
    "demoJurisdiction": "DEMO_STATE",
    "topic": "housing",
    "syntheticText": "Short synthetic rule text.",
    "plainLanguageSummary": "Short educational explanation.",
    "limitations": [
      "Synthetic text only.",
      "Does not represent current law in any jurisdiction."
    ]
  }
}
```

### Tool 3: `mock_law_issue_spot`

Purpose: Map a generalized scenario to synthetic legal topics and questions. This is the "analysis" capability, but it must stay educational and non-advisory.

Inputs:

```json
{
  "scenario": "Generalized scenario without personal identifiers. Required. Max 1000 characters.",
  "demoJurisdiction": "One of: DEMO_STATE, DEMO_CITY, DEMO_FEDERAL. Optional.",
  "maxTopics": "Integer 1-5. Optional. Defaults to 3."
}
```

Output:

```json
{
  "notLegalAdvice": true,
  "sourceKind": "SIMULATED_CORPUS",
  "disclaimer": "This is a simulated laws demo...",
  "scenarioAccepted": true,
  "possibleTopics": [
    {
      "topic": "housing",
      "reason": "The scenario mentions a rental unit and notice.",
      "relatedDemoCitations": ["DEMO-CIV-101", "DEMO-CIV-102"],
      "questionsForAProfessional": [
        "What jurisdiction and dates apply?",
        "What written notices or agreements exist?"
      ],
      "safeEducationalNote": "Synthetic law often turns on notice, timing, and documentation."
    }
  ],
  "cannotDetermine": [
    "Real jurisdiction",
    "Applicable current law",
    "Deadlines",
    "Likely legal outcome"
  ]
}
```

The issue-spotting tool should not produce "you should sue", "you must file", "you are entitled to", "the deadline is", "this is illegal", or "the other party violated the law" style conclusions.

## Java SPI And Spring AI Code-Shaped Examples

These snippets are examples for later planning. They are not intended to be copied into production during this sprint.

### Minimal Trusted Plugin SPI

```java
package io.mindspice.magenta2.avatar.plugin;

import java.util.List;
import java.util.Set;
import org.springframework.ai.tool.ToolCallbackProvider;

public interface AvatarPlugin {
    PluginDescriptor descriptor();

    default List<ToolContribution> tools(PluginContext context) {
        return List.of();
    }
}

public record PluginDescriptor(
    String id,
    String displayName,
    String version,
    Set<PluginCapability> capabilities,
    String safetyNotice
) {}

public enum PluginCapability {
    READ_ONLY_TOOL,
    TRUSTED_LOCAL_SCRIPT,
    AVATAR_PANEL
}

public record ToolContribution(
    String toolName,
    ToolCallbackProvider provider,
    boolean defaultEnabled,
    Set<ToolRisk> risks
) {}

public enum ToolRisk {
    LEGAL_DOMAIN,
    SENSITIVE_USER_FACTS,
    READ_ONLY,
    SIMULATED_DATA_ONLY
}

public interface PluginContext {
    String magentaVersion();
    boolean pluginEnabled(String pluginId);
}
```

### Startup Catalog Using `ServiceLoader`

```java
package io.mindspice.magenta2.avatar.plugin;

import java.util.List;
import java.util.ServiceLoader;

public final class AvatarPluginCatalog {
    private final List<AvatarPlugin> plugins;

    public AvatarPluginCatalog(ClassLoader classLoader) {
        this.plugins = ServiceLoader.load(AvatarPlugin.class, classLoader)
            .stream()
            .map(ServiceLoader.Provider::get)
            .toList();
    }

    public List<AvatarPlugin> plugins() {
        return plugins;
    }

    public List<ToolContribution> toolContributions(PluginContext context) {
        return plugins.stream()
            .filter(plugin -> context.pluginEnabled(plugin.descriptor().id()))
            .flatMap(plugin -> plugin.tools(context).stream())
            .toList();
    }
}
```

Provider file for a trusted plugin jar:

```text
META-INF/services/io.mindspice.magenta2.avatar.plugin.AvatarPlugin
```

```text
example.avatar.laws.MockLawsPlugin
```

### Mock Laws Plugin Descriptor

```java
package example.avatar.laws;

import io.mindspice.magenta2.avatar.plugin.AvatarPlugin;
import io.mindspice.magenta2.avatar.plugin.PluginCapability;
import io.mindspice.magenta2.avatar.plugin.PluginContext;
import io.mindspice.magenta2.avatar.plugin.PluginDescriptor;
import io.mindspice.magenta2.avatar.plugin.ToolContribution;
import io.mindspice.magenta2.avatar.plugin.ToolRisk;
import java.util.List;
import java.util.Set;

public final class MockLawsPlugin implements AvatarPlugin {
    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
            "example.mock-laws",
            "Mock Laws Demo",
            "0.1.0",
            Set.of(PluginCapability.READ_ONLY_TOOL),
            "Simulated law corpus only; not legal advice."
        );
    }

    @Override
    public List<ToolContribution> tools(PluginContext context) {
        MockLawsTools tools = new MockLawsTools(new MockLawService(MockLawCorpus.demo()));
        return List.of(new ToolContribution(
            "mock_law_search",
            MockLawsToolProvider.provider(tools),
            false,
            Set.of(ToolRisk.LEGAL_DOMAIN, ToolRisk.READ_ONLY, ToolRisk.SIMULATED_DATA_ONLY)
        ));
    }
}
```

The descriptor is intentionally not enough to approve the tool. Approval remains a Magenta agent/profile decision.

### Spring Boot Auto-Configuration Shape

For a trusted developer plugin jar, Spring Boot auto-configuration is cleaner than component scanning arbitrary packages:

```java
package example.avatar.laws.autoconfigure;

import example.avatar.laws.MockLawCorpus;
import example.avatar.laws.MockLawService;
import example.avatar.laws.MockLawsTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "magenta.avatar.plugins.mock-laws", name = "enabled", havingValue = "true")
public class MockLawsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    MockLawService mockLawService() {
        return new MockLawService(MockLawCorpus.demo());
    }

    @Bean
    @ConditionalOnMissingBean
    MockLawsTools mockLawsTools(MockLawService service) {
        return new MockLawsTools(service);
    }

    @Bean
    ToolCallbackProvider mockLawsToolCallbackProvider(MockLawsTools tools) {
        return MethodToolCallbackProvider.builder()
            .toolObjects(tools)
            .build();
    }
}
```

Auto-configuration imports file:

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

```text
example.avatar.laws.autoconfigure.MockLawsAutoConfiguration
```

This contributes ordinary Spring AI callbacks. Magenta's existing `ChatToolRegistry` can discover them through Spring injection, but model visibility still depends on approved tool names.

### Spring AI Tool Object Shape

```java
package example.avatar.laws;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public final class MockLawsTools {
    private final MockLawService service;

    public MockLawsTools(MockLawService service) {
        this.service = service;
    }

    @Tool(
        name = "mock_law_search",
        description = "Search a simulated law corpus for Magenta plugin demos. Results are not real law and are not legal advice."
    )
    public MockLawSearchResponse search(
        @ToolParam(description = "Plain-language search text. Do not include personal identifiers. Max 400 characters.")
        String query,
        @ToolParam(required = false, description = "Synthetic jurisdiction: DEMO_STATE, DEMO_CITY, or DEMO_FEDERAL.")
        String demoJurisdiction,
        @ToolParam(required = false, description = "Synthetic topic such as housing, employment, privacy, consumer, procedure, or general.")
        String topic,
        @ToolParam(required = false, description = "Maximum results from 1 to 5. Defaults to 3.")
        Integer maxResults
    ) {
        return service.search(query, demoJurisdiction, topic, maxResults);
    }

    @Tool(
        name = "mock_law_read",
        description = "Read one synthetic demo law entry by mock citation. This does not retrieve real legal text."
    )
    public MockLawReadResponse read(
        @ToolParam(description = "Synthetic citation such as DEMO-CIV-101.")
        String demoCitation
    ) {
        return service.read(demoCitation);
    }

    @Tool(
        name = "mock_law_issue_spot",
        description = "Identify educational topics in a generalized scenario using only the simulated demo corpus. Not legal advice."
    )
    public MockLawIssueSpotResponse issueSpot(
        @ToolParam(description = "Generalized scenario without names, addresses, case numbers, or other personal identifiers. Max 1000 characters.")
        String scenario,
        @ToolParam(required = false, description = "Synthetic jurisdiction: DEMO_STATE, DEMO_CITY, or DEMO_FEDERAL.")
        String demoJurisdiction,
        @ToolParam(required = false, description = "Maximum synthetic topics from 1 to 5. Defaults to 3.")
        Integer maxTopics
    ) {
        return service.issueSpot(scenario, demoJurisdiction, maxTopics);
    }
}
```

Magenta's current tools often return JSON strings after `ObjectMapper` serialization. Returning records directly can work if Spring AI serializes them appropriately in the pinned version, but Magenta should verify Spring AI 1.1.4 behavior before implementation. To match existing Magenta style exactly, wrap responses in `ObjectMapper.writeValueAsString(...)` and return `String`.

### Service And Records Shape

```java
package example.avatar.laws;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MockLawService {
    static final String DISCLAIMER = """
        This is a simulated laws demo for Magenta plugin research. It is not real law,
        not legal advice, and does not create an attorney-client relationship. For a
        real legal issue, consult a qualified legal professional or official legal source.
        """;

    private static final Set<String> DEMO_JURISDICTIONS = Set.of("DEMO_STATE", "DEMO_CITY", "DEMO_FEDERAL");

    private final MockLawCorpus corpus;

    public MockLawService(MockLawCorpus corpus) {
        this.corpus = corpus;
    }

    public MockLawSearchResponse search(String query, String demoJurisdiction, String topic, Integer maxResults) {
        SafetyCheck safety = SafetyCheck.forText(query, 400);
        if (!safety.accepted()) {
            return MockLawSearchResponse.refused(DISCLAIMER, safety.reason());
        }

        String normalizedJurisdiction = normalizeJurisdiction(demoJurisdiction);
        int limit = clamp(maxResults, 3, 1, 5);
        List<MockLawEntry> matches = corpus.entries().stream()
            .filter(entry -> normalizedJurisdiction == null || entry.demoJurisdiction().equals(normalizedJurisdiction))
            .filter(entry -> topic == null || topic.isBlank() || entry.topic().equalsIgnoreCase(topic.trim()))
            .filter(entry -> matchesQuery(entry, query))
            .limit(limit)
            .toList();

        return MockLawSearchResponse.accepted(DISCLAIMER, matches);
    }

    public MockLawReadResponse read(String demoCitation) {
        if (demoCitation == null || !demoCitation.matches("DEMO-[A-Z]+-[0-9]{3}")) {
            return MockLawReadResponse.notFound(DISCLAIMER, "Only synthetic demo citations are supported.");
        }
        return corpus.find(demoCitation)
            .map(entry -> MockLawReadResponse.found(DISCLAIMER, entry))
            .orElseGet(() -> MockLawReadResponse.notFound(DISCLAIMER, "Synthetic citation was not found."));
    }

    public MockLawIssueSpotResponse issueSpot(String scenario, String demoJurisdiction, Integer maxTopics) {
        SafetyCheck safety = SafetyCheck.forText(scenario, 1000);
        if (!safety.accepted()) {
            return MockLawIssueSpotResponse.refused(DISCLAIMER, safety.reason());
        }
        String normalizedJurisdiction = normalizeJurisdiction(demoJurisdiction);
        int limit = clamp(maxTopics, 3, 1, 5);
        List<MockIssueTopic> topics = corpus.entries().stream()
            .filter(entry -> normalizedJurisdiction == null || entry.demoJurisdiction().equals(normalizedJurisdiction))
            .filter(entry -> scenarioMentionsTopic(scenario, entry.topic()))
            .limit(limit)
            .map(entry -> new MockIssueTopic(
                entry.topic(),
                "The generalized scenario appears related to this synthetic topic.",
                List.of(entry.demoCitation()),
                List.of(
                    "Which real jurisdiction and dates apply?",
                    "What documents, notices, or communications exist?",
                    "What deadlines or procedural rules would a qualified professional check?"
                ),
                "Synthetic topic match only; this is not a legal conclusion."
            ))
            .toList();
        return MockLawIssueSpotResponse.accepted(DISCLAIMER, topics);
    }

    private String normalizeJurisdiction(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!DEMO_JURISDICTIONS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported synthetic jurisdiction: " + value);
        }
        return normalized;
    }

    private boolean matchesQuery(MockLawEntry entry, String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return entry.title().toLowerCase(Locale.ROOT).contains(normalized)
            || entry.summary().toLowerCase(Locale.ROOT).contains(normalized)
            || entry.topic().toLowerCase(Locale.ROOT).contains(normalized);
    }

    private boolean scenarioMentionsTopic(String scenario, String topic) {
        return scenario != null && scenario.toLowerCase(Locale.ROOT).contains(topic.toLowerCase(Locale.ROOT));
    }

    private int clamp(Integer value, int defaultValue, int min, int max) {
        int candidate = value == null ? defaultValue : value;
        return Math.max(min, Math.min(max, candidate));
    }
}

public record MockLawEntry(
    String demoCitation,
    String title,
    String demoJurisdiction,
    String topic,
    String syntheticText,
    String summary
) {}

public record MockLawSearchResponse(
    boolean notLegalAdvice,
    String sourceKind,
    String disclaimer,
    boolean queryAccepted,
    List<String> warnings,
    List<MockLawEntry> results
) {
    static MockLawSearchResponse accepted(String disclaimer, List<MockLawEntry> results) {
        return new MockLawSearchResponse(true, "SIMULATED_CORPUS", disclaimer, true, List.of(), results);
    }

    static MockLawSearchResponse refused(String disclaimer, String reason) {
        return new MockLawSearchResponse(true, "SIMULATED_CORPUS", disclaimer, false, List.of(reason), List.of());
    }
}
```

### Synthetic Corpus Shape

```java
package example.avatar.laws;

import java.util.List;
import java.util.Optional;

public record MockLawCorpus(List<MockLawEntry> entries) {
    public static MockLawCorpus demo() {
        return new MockLawCorpus(List.of(
            new MockLawEntry(
                "DEMO-CIV-101",
                "Quiet Enjoyment In Demo Residential Leases",
                "DEMO_STATE",
                "housing",
                "A demo landlord shall not substantially interfere with a demo tenant's ordinary use of a demo dwelling.",
                "Synthetic housing rule about interference with a rental unit."
            ),
            new MockLawEntry(
                "DEMO-CIV-102",
                "Demo Notice Before Non-Emergency Entry",
                "DEMO_STATE",
                "housing",
                "A demo landlord should provide reasonable demo notice before non-emergency entry.",
                "Synthetic housing rule about notice before entry."
            ),
            new MockLawEntry(
                "DEMO-LAB-201",
                "Demo Final Pay Timing",
                "DEMO_STATE",
                "employment",
                "A demo employer should provide final demo wages within the synthetic timing window.",
                "Synthetic employment rule about final pay."
            ),
            new MockLawEntry(
                "DEMO-PRI-301",
                "Demo Consumer Data Correction",
                "DEMO_FEDERAL",
                "privacy",
                "A demo data holder should provide a synthetic correction workflow for demo consumer records.",
                "Synthetic privacy rule about correction requests."
            )
        ));
    }

    public Optional<MockLawEntry> find(String demoCitation) {
        return entries.stream()
            .filter(entry -> entry.demoCitation().equals(demoCitation))
            .findFirst();
    }
}
```

### Safety Check Shape

```java
package example.avatar.laws;

import java.util.regex.Pattern;

public record SafetyCheck(boolean accepted, String reason) {
    private static final Pattern OBVIOUS_PII = Pattern.compile(
        "(?i)(\\b\\d{3}-\\d{2}-\\d{4}\\b|\\b\\d{3}[-. ]\\d{3}[-. ]\\d{4}\\b|\\bcase\\s*#?\\s*[a-z0-9-]{4,}\\b)"
    );

    public static SafetyCheck forText(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return new SafetyCheck(false, "Input is required.");
        }
        if (text.length() > maxLength) {
            return new SafetyCheck(false, "Input is too long for the demo tool.");
        }
        if (OBVIOUS_PII.matcher(text).find()) {
            return new SafetyCheck(false, "Remove personal identifiers and ask with a generalized scenario.");
        }
        return new SafetyCheck(true, "");
    }
}
```

This is not a complete privacy filter. It is only a guardrail example. A production design should assume PII detection is fallible and should avoid persistence of raw legal scenarios by default.

## Kawa Trusted-Script Example

This is useful only if a later trusted local scripting host exists. It is intentionally written against a narrow `magenta` host API rather than direct Spring internals.

```scheme
;; Trusted local script only. Not a sandbox.

(define disclaimer
  "This is a simulated laws demo for Magenta plugin research. It is not real law, not legal advice, and does not create an attorney-client relationship. For a real legal issue, consult a qualified legal professional or official legal source.")

(define schema
  "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"description\":\"Generalized demo query without personal identifiers.\"},\"maxResults\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":5}},\"required\":[\"query\"]}")

(define (mock-law-search args ctx)
  (let ((query (invoke args 'get "query")))
    (if (or (eq? query #!null) (> (invoke query 'length) 400))
        (invoke ctx 'json
          "notLegalAdvice" #t
          "sourceKind" "SIMULATED_CORPUS"
          "disclaimer" disclaimer
          "queryAccepted" #f
          "warnings" (list "Input is required and must be under 400 characters."))
        (invoke ctx 'json
          "notLegalAdvice" #t
          "sourceKind" "SIMULATED_CORPUS"
          "disclaimer" disclaimer
          "queryAccepted" #t
          "results" (list
            (invoke ctx 'object
              "demoCitation" "DEMO-CIV-101"
              "title" "Quiet Enjoyment In Demo Residential Leases"
              "demoJurisdiction" "DEMO_STATE"
              "topic" "housing"
              "summary" "Synthetic educational summary.")))))))

(invoke magenta 'registerReadOnlyTool
  "mock_law_search"
  "Search a simulated law corpus for Magenta plugin demos. Results are not real law and are not legal advice."
  schema
  mock-law-search)
```

Safety implications:

- This script must be operator-trusted.
- It must not receive `JdbcTemplate`, `ApplicationContext`, filesystem roots, HTTP clients, shell services, or raw classloader access through the host API.
- Because Kawa itself can call Java methods, host API minimization is not a sufficient sandbox. It only reduces accidental misuse by trusted local scripts.

## Data And Privacy Boundaries

The mock laws tool should be designed to avoid legal-matter data collection, not merely to protect data after collection.

Input policy:

- Ask for generalized facts only.
- Reject obvious PII patterns.
- Do not require names, addresses, dates of birth, phone numbers, case numbers, court names, employer names, landlord names, immigration identifiers, account numbers, medical details, or precise addresses.
- Do not accept document uploads in this tool.

Persistence policy:

- Do not write to `avatar.sqlite`.
- Do not write to `magenta.sqlite`.
- Do not write files.
- Do not call external services.
- If the existing audit path records tool calls, keep previews short and redacted.
- If later production code needs richer auditing, store only structured metadata such as tool name, synthetic citation ids, accepted/refused status, and coarse topic.

Prompt/context policy:

- Do not add full scenario text to durable facts or memory.
- Do not summarize user legal facts into Avatar facts unless a separate user-approved legal/privacy policy exists.
- Do not train or tune on legal scenario transcripts.

Output policy:

- Always include the disclaimer.
- Always label citations as synthetic demo citations.
- Never say "under the law" without "in the simulated demo corpus."
- Never provide deadlines, filing instructions, demand-letter text, settlement strategy, procedural next steps, or real-world jurisdiction conclusions.
- Encourage official sources or qualified counsel for real issues.

## Prompt And Tool Approval Policy

### Agent/Profile Approval

Later implementation should require explicit approved tool names on the Avatar or test agent profile:

```json
{
  "approvedTools": [
    "mock_law_search",
    "mock_law_read",
    "mock_law_issue_spot"
  ]
}
```

Do not approve `"*"` for Avatar plugin demonstrations. Even though the current registry supports wildcard resolution, legal-themed plugin tools should be governed as explicit capabilities.

### Mode Policy

Recommended initial mode exposure:

| Mode | Mock Laws Tool Availability | Reason |
| --- | --- | --- |
| NORMAL | Allowed only when explicitly approved. | Avatar may answer educational demo questions. |
| PLAN | Not available by default. | Existing PLAN allowlist excludes it; avoid legal-themed planning scaffolds. |
| TASK | Not available by default. | Existing TASK allowlist excludes it; avoid task definitions that imply legal work. |
| EXECUTE_PLAN | Not available unless explicitly approved and separately reviewed. | Execution contexts can create deliverables; legal-themed deliverables are high risk. |
| EXECUTE_TASK | Not available unless explicitly approved and separately reviewed. | Same as execution plan. |

### System Prompt Policy

Avatar prompt text for this tool family should include:

```text
When using mock_law_* tools, treat all results as simulated demo data only.
Do not present mock law results as real law, legal advice, legal research, or a
professional recommendation. Do not infer real deadlines, rights, liabilities,
procedures, or outcomes. If the user describes a real legal matter, state that
Magenta cannot provide legal advice and suggest consulting a qualified legal
professional or official legal source.
```

### Tool Result Handling

The model may summarize tool results, but it must preserve these fields in meaning:

- `notLegalAdvice: true`
- `sourceKind: SIMULATED_CORPUS`
- `disclaimer`
- synthetic citation names
- warnings/refusals

The model must not convert a synthetic topic match into a legal conclusion.

### Human Approval

No separate human approval is needed for a read-only demo answer that stays within the above boundaries. Human approval would be required before any future capability:

- drafts or edits legal documents;
- contacts third parties;
- submits forms;
- files anything;
- stores legal facts as durable Avatar facts;
- searches or summarizes real legal sources;
- creates tasks for real legal action.

## Why Not Implement Runtime In This Sprint

Plugin runtime should remain out of the Avatar sprint for concrete engineering reasons:

1. The sprint's current architecture direction says Avatar should use existing runtime services and plugin runtime is deferred research-only.
2. A plugin runtime is not just a `ToolCallbackProvider`; it needs activation, versioning, dependency loading, failure isolation, audit semantics, configuration, UI, tests, rollback, and support boundaries.
3. Trusted Java SPI plugins inherit host process privileges; untrusted plugins require a different isolation design.
4. Kawa's value is Java interop, which means it is unsafe as an untrusted in-process plugin language.
5. Legal-themed tools add product safety risk. The mock demo must be tightly governed before any generic plugin mechanism can expose it.
6. The existing Avatar Phase 04 plan already has first-party organizer, notes, calendar, output, and research-assignment tools to build. Those should prove the Avatar assistant loop before plugin abstractions are introduced.
7. A runtime would create schema/docs/API/UI blast radius that is not justified by a single demonstration tool.
8. Real law lookup is a separate high-stakes retrieval problem and should not be smuggled in through a mock plugin example.

The correct sprint outcome is this research artifact plus, at most, a later separately approved plan for a first-party synthetic demo tool. A generic runtime should wait until Avatar's first-party assistant behavior, tool approval UX, and audit story are stable.

## Follow-ups

- Create a later implementation plan only if the user explicitly wants the mock laws demo built.
- If built, start as a first-party synthetic tool under an explicit feature flag, not as a plugin runtime.
- Confirm Spring AI 1.1.4 return-type behavior for record-returning `@Tool` methods; otherwise follow Magenta's existing JSON-string pattern.
- Add tests for `ToolCallbackProvider` schema exposure, registry approval, disclaimer enforcement, PII refusal, synthetic-citation-only reads, and mode filtering.
- Decide whether Avatar should have a profile-level governance setting that rejects wildcard approval when plugin tools are registered.
- Research out-of-process plugin isolation before accepting untrusted Java, Kawa, or marketplace plugins.
- If real-law lookup is later requested, produce a separate source-ingestion plan using Congress.gov, OLRC/U.S. Code, GovInfo, Federal Register/eCFR, and relevant state official sources.
