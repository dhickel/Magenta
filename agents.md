# Magenta: The Agentic Framework

## 1. Mission Statement
Magenta is designed to be a highly adaptable, high-level framework for experimenting with LLM and Agentic architectures. It serves as a "last-layer" platform, effectively bootstrapping the development of complex autonomous systems without requiring low-level reimplementation of core agent loops.

While initially serving as a daily driver for software development and productivity, its ultimate trajectory is to support:
*   **Self-Evolving Agents:** Integration with code interpreters and self-modifying memory structures.
*   **Open-Ended Learning:** Systems that can grow their knowledge base autonomously over time.
*   **Rapid Prototyping:** A stable foundation to test new agent theories (memory models, delegation patterns) immediately.

## 2. Core Design Philosophies

### A. Efficiency Over Personality
Internal agents and tools prioritize **Token Efficiency** and **Functional Precision** over conversational flair.
*   **No "Spice":** Sub-agents and internal tools must be strictly utilitarian to minimize context window usage.
*   **Direct Output:** Results are returned in formats optimized for machine parsing, not human entertainment, unless targeting a user-facing interaction layer.

### B. Flexible Safety Modes
The framework supports strict operational modes to balance autonomy with safety:
1.  **Non-Destructive Mode:** Read-only access. No file writes, no shell execution, no external API side-effects.
2.  **Prompt-on-Destructive Mode:** (Default) The agent must explicitly request user confirmation before performing any state-changing action (writing files, deleting data, executing commands).
3.  **Destructive Mode:** Full autonomy. The agent is trusted to modify the environment freely. *Use with caution.*

### C. Hierarchical & Dynamic Structure
*   **Current State:** A strict Hierarchical Model (Master Agent -> Sub-Agents). The Master delegates specific sub-tasks to specialized workers.
*   **Future State:** A Dynamic Network where agents can autonomously instantiate peers, hire outside help, or form ad-hoc teams based on task complexity.

## 3. Knowledge & Memory Architecture

The framework abstracts memory into a flexible interface, allowing specific implementations to vary based on the agent's purpose.

*   **Dual-Scope Persistence:**
    *   **Session Scratchpad:** Ephemeral memory for the current task chain.
    *   **Evolving Brain:** A persistent, vector-backed store for long-term accumulation of concepts, preferences, and learned facts.
*   **Conceptual Retrieval:**
    *   Support for "Conceptual Prompting" (e.g., `/prompt-memory`).
    *   The system allows querying for *concepts* (abstract ideas, patterns) rather than just keyword matching, leveraging embedding spaces.

## 4. Interaction Layer

The interaction layer is designed for "Power Users" and developers.

*   **CLI First:** A robust command-line interface.
*   **Internal Formatted Prompting:** The ability to map shorthand commands to complex, pre-optimized system prompts.
    *   *Example:* A command to inject a specific memory search context before the user's actual query.
*   **Command Dispatcher:**
    *   `/mode`: Switch safety levels.
    *   `/spawn`: Manually invoke specific agent types.
    *   `/memory`: Direct interaction with the vector store.

## 5. Roadmap Summary
1.  **Core Framework:** Establish the Service/Agent/Tool abstraction layers.
2.  **Safety & CLI:** Implement the Command Dispatcher and Safety Interceptors.
3.  **Hierarchical Delegation:** Enable the Master Agent to reliably spawn and manage sub-agents.
4.  **Memory Integration:** Build the Vector Store and Conceptual Retrieval tools.
5.  **Self-Evolution Experiments:** Begin integrating interpreter loops and self-editing memory logic.
