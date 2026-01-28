# Getting Started with Magenta

## Prerequisites

- Java 21 or higher
- Maven 3.8+
- Ollama (or OpenAI/Anthropic API key)

## Quick Start

### 1. Build the Project

```bash
cd Magenta
mvn clean compile
```

### 2. Configure Your Agent

Create or edit `config.json`:

```json
{
  "global": {
    "base_agent": "assistant",
    "storage_path": "data/magenta.db",
    "stream_delay_ms": 20
  },
  "endpoints": {
    "ollama-local": {
      "type": "ollama",
      "base_url": "http://localhost:11434"
    }
  },
  "models": {
    "llama-3.3-70b": {
      "model_name": "llama3.3:70b",
      "endpoint": "ollama-local",
      "streaming": true,
      "max_tokens": 8000,
      "temperature": 0.7
    }
  },
  "agents": {
    "assistant": {
      "model": "llama-3.3-70b",
      "tools": ["shell", "filesystem", "web"],
      "system_prompt_key": "assistant_prompt",
      "color": "CYAN"
    }
  },
  "prompts": {
    "assistant_prompt": "You are a helpful AI assistant..."
  },
  "security": {
    "approval_required_for": ["shell", "web_fetch"],
    "always_allow_commands": ["ls", "pwd", "echo"],
    "blocked_commands": ["rm -rf /", "mkfs"]
  },
  "colors": {
    "PRIMARY": "cyan",
    "SUCCESS": "green",
    "ERROR": "red",
    "WARNING": "yellow",
    "AI_RESPONSE": "cyan"
  }
}
```

### 3. Run Magenta

```bash
mvn exec:java -Dexec.mainClass="com.magenta.Main"
```

Or with custom config:

```bash
mvn exec:java -Dexec.mainClass="com.magenta.Main" -Dexec.args="--config /path/to/config.json"
```

### 4. Interact with Your Agent

```
magenta> Hello!
[Agent responds in cyan]

magenta> List files in current directory
[Agent uses shell tool to run 'ls']

magenta> /help
[Shows available commands]

magenta> /exit
[Exits application]
```

## Understanding the Configuration

### Global Settings

```json
"global": {
  "base_agent": "assistant",      // Which agent to load on startup
  "storage_path": "data/magenta.db",  // Database location
  "stream_delay_ms": 20           // Character delay for smooth output
}
```

### Endpoints

```json
"endpoints": {
  "ollama-local": {
    "type": "ollama",
    "base_url": "http://localhost:11434"
  },
  "openai-api": {
    "type": "openai",
    "api_key": "${OPENAI_API_KEY}"  // Reads from environment variable
  },
  "anthropic-api": {
    "type": "anthropic",
    "api_key": "${ANTHROPIC_API_KEY}"
  }
}
```

### Models

```json
"models": {
  "llama-3.3-70b": {
    "model_name": "llama3.3:70b",  // Actual model identifier
    "endpoint": "ollama-local",     // Which endpoint to use
    "streaming": true,              // Enable token streaming
    "max_tokens": 8000,
    "temperature": 0.7
  }
}
```

### Agents

```json
"agents": {
  "assistant": {
    "model": "llama-3.3-70b",              // Which model to use
    "tools": ["shell", "filesystem"],       // Available tools
    "system_prompt_key": "assistant_prompt",  // System message
    "color": "CYAN"                         // Output color
  },
  "coder": {
    "model": "llama-3.3-70b",
    "tools": ["filesystem", "shell"],
    "system_prompt_key": "coder_prompt",
    "color": "GREEN"
  }
}
```

### Security

```json
"security": {
  "approval_required_for": ["shell", "web_fetch"],  // Tools that need approval
  "always_allow_commands": ["ls", "pwd"],           // Auto-approved commands
  "blocked_commands": ["rm -rf /", "mkfs"]          // Blocked commands
}
```

## Available Tools

| Tool | Description | Config Name |
|------|-------------|-------------|
| Shell | Execute shell commands | `"shell"` |
| Filesystem | Read/write files | `"filesystem"` |
| Web | Fetch web pages | `"web"` |
| Todo | Manage tasks | `"todo"` |
| Knowledge | Vector store search | `"knowledge"` |
| Delegate | Create sub-agents | `"delegate"` |

## Slash Commands

| Command | Description |
|---------|-------------|
| `/exit` or `/quit` | Exit the application |
| `/help` | Show help message |
| `/clear` | Clear the terminal screen |
| `/history` | Show conversation history (planned) |

## Example Interactions

### File Operations

```
magenta> Create a file called "hello.txt" with the content "Hello, World!"
[Agent uses filesystem tool]

magenta> What's in hello.txt?
[Agent reads file and responds]
```

### Shell Commands

```
magenta> What files are in the current directory?
[Agent runs 'ls' and describes files]

magenta> Create a new directory called "test"
[Prompts for approval to run 'mkdir test']
```

### Web Fetching

```
magenta> What's on the front page of example.com?
[Agent fetches and summarizes webpage]
```

## Creating a Custom Agent

### 1. Add Agent to Config

```json
"agents": {
  "my-agent": {
    "model": "llama-3.3-70b",
    "tools": ["shell", "filesystem"],
    "system_prompt_key": "my_prompt",
    "color": "GREEN"
  }
}
```

### 2. Add System Prompt

```json
"prompts": {
  "my_prompt": "You are a specialized assistant that focuses on..."
}
```

### 3. Run with Your Agent

```bash
# Update global.base_agent to "my-agent"
# Or modify Main.java to load different agent
```

## Next Steps

- [Architecture Overview](../architecture/overview.md) - Understand the system design
- [Creating Agents Guide](creating-agents.md) - Build custom agents
- [Extending I/O](extending-io.md) - Add new I/O contexts
- [Custom Tools](custom-tools.md) - Create specialized tools
- [Security Configuration](security-config.md) - Configure security policies

## Troubleshooting

### Agent doesn't respond

**Check:**
1. Is Ollama running? (`ollama serve`)
2. Is the model pulled? (`ollama pull llama3.3:70b`)
3. Is the endpoint URL correct in config?
4. Check terminal for error messages

### Tool execution fails

**Check:**
1. Is the tool in the agent's tools list?
2. Did you approve the command? (if approval required)
3. Is the command blocked in security config?
4. Check terminal for security messages

### Configuration not loading

**Check:**
1. Is the JSON valid? (use a JSON validator)
2. Is the file path correct?
3. Are all required fields present?
4. Check for typos in keys

## Getting Help

- Read the [documentation](../README.md)
- Check [issues](https://github.com/your-repo/magenta/issues)
- Review configuration examples in `examples/`
