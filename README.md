# Magenta - Autonomous Multi-Agent System

Magenta is a Java-based autonomous agent system capable of managing tasks, executing shell commands, browsing the web, and storing long-term knowledge. It uses `LangChain4j` and local LLMs (via Ollama).

## Prerequisites

1.  **Java 25** (or compatible JDK)
2.  **Maven**
3.  **Ollama** running locally with the `devstral2` model (or configure your own in `Main.java`).

## Setup

1.  Start Ollama: `ollama serve`
2.  Pull the model: `ollama pull devstral2` (or your preferred model)
3.  Build the project: `mvn clean install`

## Usage

Run the application:
```bash
mvn exec:java -Dexec.mainClass="com.magenta.Main"
```

## Commands

- `/help` - Show available commands.
- `/tools` - List active tools.
- `/exit` - Quit the application.

## Architecture

- **Domain**: recursive `Task` management.
- **Tools**: FileSystem, Shell, Web, Knowledge (Vector Store), Delegation.
- **Memory**: Short-term chat window + Long-term Vector Store (In-Memory).
