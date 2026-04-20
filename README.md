# magenta2

Minimal Spring AI + Ollama chat scaffold.

Chat memory is persisted in local SQLite (`./chat-memory.db`).

## Run

```bash
mvn spring-boot:run
```

This starts:
- the web API in the background (`/api/chat`)
- the terminal REPL in the foreground

Type directly in the terminal to chat. Useful commands:
- `/help`
- `/new`
- `/use <conversation-id>`
- `/model` or `/model <name>` or `/model default`
- `/clear` or `/clear <conversation-id>`
- `/exit`

If you want API-only mode (no terminal loop):

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--app.repl.enabled=false
```

## Chat loop (HTTP)

Start a conversation (model defaults to `gemma4-26b:32k`):

```bash
curl -s http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"Hello. Keep answers short."}'
```

Continue the same conversation by reusing `conversationId` from the previous response:

```bash
curl -s http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"<conversation-id>","message":"What did I just ask you?"}'
```

Override model per request (optional):

```bash
curl -s http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"<conversation-id>","model":"gemma4-26b:32k","message":"Reply with one sentence."}'
```

Clear a conversation from memory:

```bash
curl -i -X DELETE http://localhost:8080/api/chat/<conversation-id>
```

## Session + history bootstrap endpoints

List persisted conversation ids:

```bash
curl -s http://localhost:8080/api/chat/sessions
```

Load a conversation history:

```bash
curl -s http://localhost:8080/api/chat/<conversation-id>/history
```

Run command-style session actions:

```bash
curl -s http://localhost:8080/api/chat/commands \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"<active-id>","command":"/new"}'
```

Supported commands:
- `/new`
- `/switch <uuid>`
- `/clear`
- `/clear <uuid>`

## Browser demo

Open `http://localhost:8080/chat` for a simple chat UI that uses these endpoints.
