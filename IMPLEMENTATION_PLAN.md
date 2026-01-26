# Magenta Implementation Plan v2

This document outlines the roadmap for upgrading the Magenta agent with robust security, persistence, and configuration management.

## 1. Objectives

1.  **Security**: Prevent unauthorized shell execution and unrestricted web access.
2.  **Persistence**: Save tasks, session logs, and agent memory to SQLite databases.
3.  **Configuration**: Centralize settings (Models, Agents, Policies) in a JSON configuration file.
4.  **Generic**: Ensure systems (like approval) are modular and reusable.

## 2. Architecture Updates

### 2.1 Configuration System
A new `ConfigService` will load `config.json` at startup.

**Structure (`config.json`):**
```json
{
  "global": {
    "ollama_base_url": "http://localhost:11434",
    "default_model": "devstral2",
    "storage_path": "./data"
  },
  "security": {
    "approval_required_for": ["shell", "web_fetch", "file_write"],
    "always_allow_commands": ["ls", "pwd", "grep", "cat"],
    "blocked_commands": ["rm", "mv", "shutdown"]
  },
  "models": [
    { "name": "devstral2", "timeout_seconds": 60, "max_tokens": 4096 },
    { "name": "codellama", "timeout_seconds": 120, "max_tokens": 8192 }
  ],
  "agents": {
    "default": {
      "system_prompt": "You are a helpful assistant..."
    },
    "coding_task": {
      "model": "codellama",
      "tools": ["shell", "file_system"]
    }
  }
}
```

### 2.2 Security & Approval Layer
A `SecurityManager` class will intercept sensitive tool calls.
- **Pattern**: Decorator or Direct Invocation within Tools.
- **Flow**: `Tool` -> `SecurityManager.check(action, command)` -> `UserApproval (CLI)` -> `Execute/Block`.
- **Logic**:
    1. Check `blocked_commands` (Blacklist) -> Reject.
    2. Check `always_allow_commands` (Whitelist) -> Allow.
    3. Check `approval_required_for` -> Prompt User.
    4. Default -> Allow (or Block, depending on "paranoid mode" setting).

### 2.3 Persistence (SQLite)
Two distinct database files (or connections) managed by `DatabaseService`.

1.  **Agent Database (`agent.db`)**: Long-term, shared data.
    -   `config`: Key-value store for dynamic settings.
    -   `facts`: Learned knowledge (Key, Value, Source).
    -   `vectors`: Serialized blob of the VectorStore (simplest persistence for RAG).
    
2.  **Session/Task Database (`tasks.db`)**: Task-specific or Session-specific data.
    -   `tasks`: The Todo List (ID, Description, ParentID, Status, CreatedAt).
    -   `logs`: Audit log of actions taken (Tool usage, specific commands).

## 3. Implementation Steps

### Phase 1: Foundation & Configuration
1.  **Dependencies**: Add `org.xerial:sqlite-jdbc` and ensure `com.fasterxml.jackson.core:jackson-databind` is available.
2.  **ConfigService**: Create `com.magenta.config.ConfigService` to parse `config.json`.
3.  **Refactor Main**: Update `Main.java` to load config before initializing agents.

### Phase 2: Database Layer
1.  **DatabaseService**: Create generic JDBC wrapper for SQLite.
2.  **Schema Migration**: Create SQL scripts to initialize `tasks` and `agent` tables.
3.  **Update TodoService**: Rewrite `TodoService` to use `DatabaseService` (replace in-memory List).

### Phase 3: Security System
1.  **SecurityManager**: Implement the whitelist/blacklist and user prompt logic.
2.  **Tool Integration**:
    -   Update `ShellTools` to call `SecurityManager.requireApproval("shell", command)`.
    -   Update `WebTools` to call `SecurityManager.requireApproval("web_fetch", url)`.

### Phase 4: Integration & Testing
1.  **Wiring**: Inject `SecurityManager` and `DatabaseService` into Tools/Services in `Main.java`.
2.  **Test**: Verify that tasks persist after restart and dangerous commands trigger a prompt.

## 4. Generic Agent Task Spec
*When implementing this plan, agents should:*
1.  Read `config.json` (create default if missing).
2.  Initialize DB connections.
3.  Pass Config and DB objects to Service constructors.
4.  Ensure `SecurityManager` is the single source of truth for permissions.