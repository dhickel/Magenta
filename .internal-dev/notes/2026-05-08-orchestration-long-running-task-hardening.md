# Orchestration Long-Running Task Hardening

Model-backed task execution can now run inside orchestration assignments. A future hardening pass should add lease heartbeat or configurable lease duration for single job items that may exceed the current fixed 5-minute lease.

This is out of scope for the placeholder-execution fix because the completed change removes fabricated task outputs and adds item retry/continue policy, while heartbeat behavior only matters for long model calls that exceed one item lease window.
