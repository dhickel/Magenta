import http from "node:http";

const port = Number(process.env.STUB_PORT || 18089);
let requestCounter = 0;

const server = http.createServer((req, res) => {
  if (req.method === "GET" && req.url === "/health") {
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ ok: true, requests: requestCounter }));
    return;
  }

  if (req.method !== "POST" || req.url !== "/v1/chat/completions") {
    res.writeHead(404, { "content-type": "application/json" });
    res.end(JSON.stringify({ error: "not found" }));
    return;
  }

  let body = "";
  req.on("data", chunk => {
    body += chunk;
  });
  req.on("end", () => {
    requestCounter += 1;
    const id = requestCounter;
    const startedAt = Date.now();
    console.log(JSON.stringify({ event: "request-start", id, bytes: body.length }));
    req.on("close", () => {
      console.log(JSON.stringify({ event: "request-close", id, elapsedMs: Date.now() - startedAt }));
    });
    res.on("close", () => {
      console.log(JSON.stringify({ event: "response-close", id, elapsedMs: Date.now() - startedAt }));
    });

    setTimeout(() => {
      if (res.writableEnded || res.destroyed) {
        console.log(JSON.stringify({ event: "response-skipped-closed", id }));
        return;
      }
      res.writeHead(200, { "content-type": "application/json" });
      res.end(JSON.stringify({
        id: `chatcmpl-phase07-${id}`,
        object: "chat.completion",
        created: Math.floor(Date.now() / 1000),
        model: "phase07-browser-stub",
        choices: [{
          index: 0,
          message: {
            role: "assistant",
            content: `phase07 browser stub completion ${id}`
          },
          finish_reason: "stop"
        }],
        usage: {
          prompt_tokens: 12,
          completion_tokens: 5,
          total_tokens: 17
        }
      }));
      console.log(JSON.stringify({ event: "response-sent", id, elapsedMs: Date.now() - startedAt }));
    }, 4500);
  });
});

server.listen(port, "127.0.0.1", () => {
  console.log(JSON.stringify({ event: "listening", port }));
});
