# Task Execution SSE Follow-ups

Direct task run SSE now uses model-backed task execution, but the current controller bridges the execution Flux by blocking inside the request handler. A future pass should consider returning a native reactive endpoint or running the blocking model execution on an explicit executor if live task runs need higher concurrency.

This is out of scope for the placeholder-execution fix because the behavioral bug was fabricated outputs, not HTTP threading.
