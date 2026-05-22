# Scope

Review why a completed plan run persisted `comparisonFile.txt` whose content was the literal filename `shed_comparison.md`, instead of preserving the actual markdown deliverable, and map the surrounding output/input problem space before implementation.

Observed incident:

- Run: `00f2fd57-e962-42cb-bafb-9876bc8baa71`
- Plan: `7551d6cf-fdef-49bd-88c4-a00e60abc2eb`
- Run output payload: `{"comparisonFile":"shed_comparison.md"}`
- Task `outputs_json`: `[]`
- Registered artifact: `comparisonFile`, type `text`, file `comparisonFile.txt`
- Artifact file content: `shed_comparison.md`
- No `shed_comparison.md` was present in the persisted run output directory.

Files reviewed:

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/plan/PlanSaveTools.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/BindingResolver.java`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/workflow-engine.md`

# Findings

## Direct Cause

The backend only treats a string output value as a file reference when the saved plan output field is explicitly typed as `file_path`.

`PlanService.completeRun(...)` cleans the model/tool-provided output map, checks required declared outputs, then calls `materializeRunOutputs(...)` before recording the completed run. `materializeRunOutputs(...)` builds its type map only from `task.outputs()`:

```java
Map<String, PlanFieldType> outputTypes = outputTypeMap(task.outputs());
outputArtifactService.materializeAll(run.id(), run.planId(), outputs, outputTypes, outputDir, ...);
```

For the incident run, `task.outputs()` was empty. Therefore `comparisonFile` had no declared type.

`OutputArtifactService.materializeAll(...)` defaults missing output types to `PlanFieldType.STRING`:

```java
PlanFieldType type = outputTypes.getOrDefault(name, PlanFieldType.STRING);
```

`STRING` and `NUMBER` both route to `materializeText(...)`, which writes `{outputName}.txt` containing `value.toString()`. That produced `comparisonFile.txt` with content `shed_comparison.md`.

This is backend behavior, not model weirdness. The model supplied an output value that looks like a file reference; the backend had no declared contract saying it was a file reference, so it persisted the value as text.

## Why Discovery Did Not Recover It

After explicit output materialization, `PlanService.completeRun(...)` calls `discoverLooseArtifactsForRun(...)`, which only scans `run.outputDirectory()`.

`OutputArtifactService.discoverLooseArtifacts(...)` is intentionally shallow and non-recursive. It registers regular files directly inside the persisted output directory that are not already registered.

This means discovery can recover `outputs/shed_comparison.md` if it already exists in the run output directory. It cannot recover:

- A file created in the temp workspace and later deleted by terminal cleanup.
- A file created in `workspace/` or `scratch/`.
- A file created under a nested output subdirectory.
- A file whose only reference is a string output value.

The runtime prompt tells execution agents to write deliverable files to the run-specific output directory and report bare filenames for files written there. The backend file-path materializer supports that convention for declared `file_path` outputs by resolving bare filenames relative to the output directory. The missing piece is that unknown output names never reach that file-path branch.

## Contract Gap

There are two output concepts currently sharing one loose map:

- **Structured values**: values meant to be consumed as strings, numbers, JSON, or user-facing markdown.
- **Artifact references**: values that name files created during execution and should be copied/registered as durable artifacts.

The system relies on declared `PlanFieldDefinition` output types to disambiguate these concepts. When `outputs_json` is empty, the backend silently falls back to `string`, which is safe for not losing a scalar value but unsafe for preserving file deliverables.

This matters for basic plans because they can have deliverables without typed output fields. In those cases the model may reasonably complete work by creating a file and returning its filename, while the backend interprets that filename as literal text.

## Adjacent Output Issues

1. **Materialization errors can be swallowed for plan runs.**

   `PlanService.materializeRunOutputs(...)` catches `IOException`, logs it, and lets completion continue. `OutputArtifactService.materialize(...)` can also throw `IllegalArgumentException` for bad JSON, missing files, escaping paths, or null values, and those are not caught there. The result is inconsistent terminal behavior: some artifact failures only log, while others can abort completion.

2. **Unknown output fields are accepted without validation.**

   `missingRequiredOutputs(...)` checks only declared required outputs. Extra output keys that are not in `task.outputs()` proceed through materialization as strings. This makes the artifact shape dependent on model-chosen names.

3. **`file_path` resolution is narrower than the file tool surface.**

   File tools in active assignments treat `outputs/...` as the active run output directory. `materializeFilePath(...)` treats relative paths as output-directory-relative and bare filenames as output-directory-relative, but does not appear to resolve `workspace/...`, `scratch/...`, or tool aliases using the same scope rules. This is good for confinement, but it means a model that reports a valid tool path outside the output directory may fail materialization unless it provides a data-root absolute path.

4. **Final workflow output materialization uses weaker type inference.**

   `WorkflowRunner.materializeFinalOutputs(...)` infers only number, JSON, or string. It never infers file paths from strings, so a workflow final output like `report.md` is materialized as text unless something upstream already registered task artifacts separately.

5. **Workflow binding type inference is heuristic and different from materialization.**

   `BindingResolver` treats strings starting with `/`, `./`, or `data/` as `file_path`; a bare filename like `shed_comparison.md` is inferred as `string`. This can let file-like values travel through ports as strings and later be persisted as text.

6. **Artifact identity is not part of the output payload.**

   Plan and workflow output maps carry primitive/object values, not durable artifact references. A downstream workflow node receives `{"comparisonFile":"shed_comparison.md"}` rather than something like `{artifactId, fileName, path, type}`. That makes piping outputs dependent on reinterpreting strings.

# Risk Assessment

The current behavior is conservative from a path-security perspective because unknown strings are not treated as files by default. That avoids accidental file reads/copies from arbitrary paths. The cost is reliability: any untyped file deliverable can be downgraded into a `.txt` wrapper around a filename.

The highest practical risk is silent success. The run completes, the output API shows an artifact, and the artifact content is technically non-empty, but the user does not receive the actual deliverable. This can bypass casual validation because the final message may mention a file and an artifact row exists.

The second risk is workflow composition. As soon as outputs become inputs, a bare filename is an ambiguous scalar. Without a richer artifact/reference contract, downstream nodes cannot know whether to read a file, copy it, preserve it, or pass the string through.

The third risk is overcorrecting with broad path inference. Treating every string ending in `.md` or containing path separators as a file could wrongly turn normal text into file operations, create path probing behavior, or fail valid text outputs because no such file exists.

# Recommendations

## Option 1: Require Declared Typed Outputs Before Completion

Fail or mark the run `NEEDS_REVIEW` when `outputValues` contains keys not declared in `task.outputs()`, unless the plan explicitly allows loose outputs.

Pros:

- Strongest contract and easiest behavior to reason about.
- Forces plan creation to define `comparisonFile` as `file_path`.
- Prevents accidental model-created output names from becoming durable API surface.
- Reduces ambiguity for workflow port binding.

Cons:

- Breaks current basic-plan flexibility where plans may have deliverables but no typed outputs.
- Requires better planning UI/tooling so models/users can declare outputs naturally.
- Does not recover older or loosely created runs by itself.

Best use:

- Long-term direction for saved task templates, workflows, and anything that pipes outputs into later steps.

## Option 2: Backend Heuristic for Unknown String Outputs, Confined to Run Output Directory

When an output name has no declared type and its value is a string, check whether it resolves to an existing regular file directly under the run output directory. If yes, materialize/register it as `file_path`; otherwise keep current `string` behavior.

Rules should be deliberately narrow:

- Bare filename: resolve as `outputDir/<filename>`.
- `outputs/<filename>` or current output dir basename prefix: resolve inside `outputDir`.
- No absolute paths for unknown fields.
- No `..`, no nested traversal, and likely no recursive search unless explicitly configured.
- If the candidate is missing, persist as text or mark review depending on policy.

Pros:

- Fixes the incident class without relying on model magic.
- Keeps path risk low by looking only in the already-designated output directory.
- Preserves backward compatibility for text outputs.
- Aligns with the existing runtime prompt that tells agents to report bare filenames for files written directly in the output directory.

Cons:

- Still heuristic; a text output that happens to equal an existing filename becomes a file artifact.
- Does not recover files written outside the output directory.
- Does not solve richer workflow piping by itself.

Best use:

- Near-term hardening for basic plans with loose deliverables.

## Option 3: Introduce Explicit Artifact Reference Values

Add a backend-recognized output value shape, for example:

```json
{
  "comparisonFile": {
    "kind": "artifact",
    "path": "shed_comparison.md",
    "mediaType": "text/markdown"
  }
}
```

or:

```json
{
  "comparisonFile": {
    "artifactId": "...",
    "fileName": "shed_comparison.md"
  }
}
```

The completion path would materialize/resolve artifact refs explicitly, and downstream workflows could pass artifact refs without string reinterpretation.

Pros:

- Cleanly separates text from file references.
- Works for workflows, jobs, and future subagent handoff.
- Allows downstream nodes to consume artifacts by ID/path/type rather than guessing.
- Can preserve metadata like content type, original path, size, and source node.

Cons:

- More schema/API work.
- Requires model/tool instructions and probably UI rendering updates.
- Existing task outputs need compatibility handling.

Best use:

- Medium-term foundation for robust output piping and multi-step workflows.

## Option 4: Artifact-First Completion Tooling

Add or adapt backend tools so execution reports artifacts through a dedicated mechanism rather than through arbitrary `outputValues`. For example, a `register_output_artifact` tool could accept `outputName`, `path`, and optional type, then return an artifact ID. `plan_complete` could reference artifact IDs instead of raw file paths.

Pros:

- Moves artifact registration before final completion validation.
- Gives the validator durable artifact IDs/content to inspect.
- Reduces ambiguity in `outputValues`.
- Avoids depending on natural-language/tool-call output maps.

Cons:

- Requires another tool path and model behavior changes.
- Still needs fallback behavior for loose existing outputs.
- More moving parts in execution mode.

Best use:

- Execution flows where files are first-class deliverables.

## Option 5: Expand Discovery Scope Before Temp Cleanup

Before deleting the temp workspace, scan additional safe locations for referenced filenames from output values and artifact paths: run output dir, task temp dir, and maybe active assignment workspace aliases. Copy matches into output dir and register them.

Pros:

- Can recover artifacts even when the model wrote to temp/workspace instead of output dir.
- Better user outcome for loose/basic plans.

Cons:

- Higher security and correctness risk if broad search is used.
- Filename collisions become likely.
- Recursive search can be expensive and surprising.
- Can preserve unintended files if matching is too permissive.

Best use:

- Limited recovery mode, ideally only for exact filenames explicitly reported in output values or artifact paths, with clear audit evidence.

## Recommended Direction

Use a staged fix:

1. Add narrow backend recovery for unknown string outputs that exactly reference existing files in the run output directory.
2. Treat missing referenced files as review-worthy when the value strongly looks like a file deliverable, rather than silently wrapping it in `.txt`.
3. Tighten planning/task creation so basic plans with file deliverables get typed outputs (`file_path`) whenever possible.
4. Design an explicit artifact reference contract for workflow/job/subagent piping, then update workflow final-output materialization and binding around that contract.

This balances the current incident with long-term adaptability. The immediate fix should not broadly infer files from arbitrary strings. It should only trust files already placed in the backend-designated output directory, because that directory is the durable boundary the runtime prompt and `OutputArtifactService` already agree on.

# Follow-ups

- Add tests for an undeclared output value `report.md` when `report.md` exists in the run output directory.
- Add tests for an undeclared output value `report.md` when the file is missing; decide whether this remains text, fails completion, or marks review.
- Add tests showing declared `file_path` outputs still copy/register bare filenames and reject escaped paths.
- Add workflow tests for final outputs containing file-like strings so the current behavior is explicit before changing it.
- Consider whether `discoverLooseArtifacts(...)` should remain shallow. If recursive discovery is added, it should be opt-in and collision-aware.
- Update `docs/technical/workspaces-tools-outputs.md` after a fix to document unknown-output fallback and artifact reference behavior.
