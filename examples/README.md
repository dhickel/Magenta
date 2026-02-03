# Magenta Examples

This directory contains example configurations and workflow guides to help you get started with Magenta.

## Directory Structure

```
examples/
├── config/          Example configuration files
├── workflows/       Step-by-step workflow guides
└── README.md        This file
```

## Example Configurations

### Code Assistant (`config/code-assistant.json`)

A configuration optimized for software development tasks:
- **Base Agent:** Code assistant with development focus
- **Models:** Devstral (primary), CodeLlama (reviewer)
- **Tools:** Filesystem, Git, Search, Shell, Context
- **Security:** Permissive with git read operations whitelisted
- **Agents:**
  - `code-assistant` - General purpose coding assistant
  - `reviewer` - Code review specialist
- **Task Templates:**
  - `code-review` - Comprehensive code review
  - `refactor` - Code refactoring with best practices
  - `implement-feature` - Feature implementation from requirements

**Use cases:**
- Code review and analysis
- Feature implementation
- Refactoring and code quality improvements
- Git operations and version control

### Research Agent (`config/research-agent.json`)

A configuration optimized for research and information gathering:
- **Base Agent:** Researcher specialized in information synthesis
- **Models:** Mixtral (higher temperature for creative research)
- **Tools:** Web, Knowledge, Context, Search, Filesystem
- **Security:** Strict approval required for shell and git operations
- **Agents:**
  - `researcher` - Comprehensive research agent
  - `summarizer` - Document summarization specialist
- **Task Templates:**
  - `research-topic` - Deep dive research on any topic
  - `summarize-document` - Document summarization

**Use cases:**
- Web research and information gathering
- Documentation summarization
- Knowledge base building
- Literature review

## Using Example Configurations

### Quick Start

1. **Copy an example configuration:**
   ```bash
   cp examples/config/code-assistant.json my-config.json
   ```

2. **Customize for your environment:**
   - Update `endpoints` to match your LLM endpoint (Ollama, OpenAI, etc.)
   - Adjust `model_name` to models you have available
   - Modify `prompts` to match your use case
   - Configure `security` settings for your needs

3. **Run Magenta with your configuration:**
   ```bash
   mvn exec:java -Dexec.mainClass="com.magenta.Main" -Dexec.args="--config my-config.json"
   ```

### Switching Between Configurations

You can maintain multiple configuration files for different purposes:

```bash
# Development work
mvn exec:java -Dexec.mainClass="com.magenta.Main" -Dexec.args="--config config-dev.json"

# Research tasks
mvn exec:java -Dexec.mainClass="com.magenta.Main" -Dexec.args="--config config-research.json"

# Code review
mvn exec:java -Dexec.mainClass="com.magenta.Main" -Dexec.args="--config config-review.json"
```

## Workflow Guides

The `workflows/` directory contains step-by-step guides for common use cases:

- **`multi-agent-delegation.md`** - Using multiple agents with task delegation and message passing
- **`context-management.md`** - Managing conversation context, archiving, and retrieval
- **`task-execution.md`** - Running tasks from templates with parameter substitution

## Configuration Best Practices

### Model Selection

- **Coding Tasks:** Use code-specialized models (Devstral, CodeLlama, DeepSeek Coder)
- **Research Tasks:** Use general-purpose models (Mixtral, GPT-4, Claude)
- **Fast Responses:** Use smaller models (7B-13B parameters)
- **Complex Tasks:** Use larger models (70B+ parameters)

### Token Limits

- **`max_tokens`:** Maximum tokens per response (2048-8192 typical)
- **`max_context`:** Maximum conversation context (8192-32768 typical)
- **`compact_threshold`:** When to trigger context compaction (80% of max_context recommended)

**Example calculation:**
```json
{
  "max_context": 16384,
  "compact_threshold": 13107  // 80% of 16384
}
```

### Security Configuration

**Permissive (Development):**
```json
{
  "approval_required_for": [],
  "always_allow_commands": ["git status", "git diff", "git log"],
  "blocked_commands": ["rm -rf /", "sudo rm", "format"]
}
```

**Strict (Production/Shared):**
```json
{
  "approval_required_for": ["shell", "git", "filesystem"],
  "always_allow_commands": [],
  "blocked_commands": ["rm", "delete", "format", "sudo", "chmod"]
}
```

### Prompt Design

Good prompts should:
1. Clearly define the agent's role and expertise
2. List available capabilities and tools
3. Specify the communication style
4. Provide guidelines and best practices
5. Set expectations for behavior

**Example structure:**
```
You are [role description].

# Your Capabilities
- [List of what the agent can do]

# Your Approach
- [How the agent should work]

# Guidelines
- [Best practices and constraints]
```

## Tool Configuration

Each agent can be configured with a subset of available tools:

| Tool | Purpose | Use Cases |
|------|---------|-----------|
| `filesystem` | Read/write files | Code editing, documentation |
| `git` | Version control operations | Commits, branches, status |
| `shell` | Execute bash commands | Build, test, system operations |
| `web` | Fetch web content | Research, documentation lookup |
| `search` | Text and semantic search | Code navigation, information retrieval |
| `context` | Context management | Archive conversations, retrieve history |
| `knowledge` | Vector store operations | Semantic memory, RAG |
| `agents` | Agent communication | Multi-agent workflows, delegation |
| `process` | System information | Monitor resources, list processes |

**Tool selection guidelines:**
- Only include tools the agent actually needs
- Security-sensitive tools require careful configuration
- More tools = more complexity for the agent to manage
- Start minimal, add tools as needed

## Task Templates

Task templates enable reusable workflow patterns:

```json
{
  "task_templates": {
    "template-id": {
      "name": "Human-Readable Name",
      "type": "TASK_TYPE",
      "description": "What this template does",
      "task_prompt": "Instructions with {{parameters}}",
      "required_tools": ["list", "of", "tools"],
      "parameter_specs": {
        "parameter_name": {
          "type": "string",
          "required": true,
          "description": "Parameter description"
        }
      }
    }
  }
}
```

**Task types:**
- `CODE_GENERATION` - Writing new code
- `CODE_ANALYSIS` - Analyzing existing code
- `REFACTORING` - Improving code structure
- `DOCUMENTATION` - Writing/updating docs
- `RESEARCH` - Information gathering
- `CUSTOM` - Other use cases

## Troubleshooting

### Common Issues

**"Base agent not found"**
- Check that `global.base_agent` matches an agent name in `agents`

**"Endpoint not found"**
- Verify `model.endpoint` references a valid endpoint in `endpoints`

**"Model not found"**
- Ensure `agent.model` references a model in `models`

**"System prompt not found"**
- Check that `agent.system_prompt` references a prompt in `prompts`

**Connection errors**
- Verify endpoint URL is correct and service is running
- Check firewall and network settings
- Increase `timeout_seconds` for slow connections

**Out of memory errors**
- Reduce `max_context` for the model
- Lower `compact_threshold` to compact more frequently
- Use smaller models (fewer parameters)

## Getting Help

- **Documentation:** See `../docs/` for detailed architecture and API documentation
- **Issues:** Report bugs at [GitHub Issues](https://github.com/anthropics/magenta/issues)
- **CLAUDE.md:** Project architecture and development guidelines

## Next Steps

1. Try the example configurations as-is
2. Customize them for your environment
3. Explore the workflow guides in `workflows/`
4. Create your own configurations and templates
5. Share your configurations with the community!

---

**Happy coding with Magenta!**
