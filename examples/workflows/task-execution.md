# Task Execution Workflow

This guide explains how to use task templates for reusable, parameterized workflows in Magenta.

## Overview

Task templates enable:
- Reusable workflow patterns
- Parameterized instructions
- Consistent task execution
- Tool requirements specification
- Template-based automation

## Key Concepts

### Task Template

A predefined task definition with:
- **Name:** Human-readable identifier
- **Type:** Category (CODE_GENERATION, ANALYSIS, etc.)
- **Task Prompt:** Instructions with parameter placeholders
- **Required Tools:** Tools needed to complete the task
- **Parameters:** Input values with validation

### Task Execution

When a task runs:
1. Template loaded from configuration
2. Parameters substituted into prompt
3. Task prompt added to agent's system prompt
4. Required tools verified available
5. Agent executes with task context

## Task Template Structure

```json
{
  "task_templates": {
    "template-id": {
      "name": "Display Name",
      "type": "TASK_TYPE",
      "description": "What this template does",
      "task_prompt": "Instructions with {{param1}} and {{param2}}",
      "required_tools": ["tool1", "tool2"],
      "parameter_specs": {
        "param1": {
          "type": "string",
          "required": true,
          "description": "What param1 does"
        },
        "param2": {
          "type": "string",
          "required": false,
          "description": "What param2 does"
        }
      }
    }
  }
}
```

## Using Task Templates

### List Available Templates

View all configured task templates:

```
magenta> /task list
```

Output:
```
Available task templates:
────────────────────────────────────────────────────
ID                Name              Type              Parameters
code-review       Code Review       CODE_ANALYSIS    file_path
refactor          Refactor Code     REFACTORING       file_path
implement-feature Implement Feature CODE_GENERATION   requirements
────────────────────────────────────────────────────
```

### Show Template Details

View full template information:

```
magenta> /task show code-review
```

Output:
```
Task Template: code-review
────────────────────────────────────────────────────
Name: Code Review
Type: CODE_ANALYSIS
Description: Perform a comprehensive code review of a file

Required Tools:
  - filesystem
  - context

Parameters:
  - file_path (required, string): Path to the file to review

Task Prompt:
Review the code at {{file_path}} and provide detailed feedback on:
1. Code quality and style
2. Potential bugs or edge cases
3. Security concerns
4. Performance issues
5. Suggestions for improvement

Provide specific examples and explain your reasoning.
────────────────────────────────────────────────────
```

### Run a Task (Future Feature)

Execute a task template:

```
magenta> /task run code-review file_path="src/main/java/UserService.java"
```

**Note:** Task execution is partially implemented. You can currently:
- List templates with `/task list`
- Show template details with `/task show <id>`
- Templates are available for programmatic use

Full interactive task execution is planned for a future release.

## Task Types

### CODE_GENERATION

Creating new code from requirements:

```json
{
  "implement-feature": {
    "type": "CODE_GENERATION",
    "task_prompt": "Implement: {{requirements}}\n\nGuidelines:\n1. Follow project patterns\n2. Include error handling\n3. Write tests\n4. Document public APIs"
  }
}
```

**Use cases:**
- New feature implementation
- Utility function creation
- Test case generation
- API endpoint development

### CODE_ANALYSIS

Analyzing existing code:

```json
{
  "analyze-structure": {
    "type": "CODE_ANALYSIS",
    "task_prompt": "Analyze {{directory}} for:\n1. Architecture patterns\n2. Dependencies\n3. Code smells\n4. Improvement opportunities"
  }
}
```

**Use cases:**
- Code review
- Architecture analysis
- Dependency mapping
- Security audits

### REFACTORING

Improving existing code:

```json
{
  "refactor": {
    "type": "REFACTORING",
    "task_prompt": "Refactor {{file_path}} to:\n1. Improve maintainability\n2. Apply design patterns\n3. Reduce complexity\n4. Preserve functionality"
  }
}
```

**Use cases:**
- Code cleanup
- Pattern application
- Performance optimization
- Technical debt reduction

### DOCUMENTATION

Creating or updating documentation:

```json
{
  "document-api": {
    "type": "DOCUMENTATION",
    "task_prompt": "Document the API in {{file_path}}:\n1. Public methods\n2. Parameters and returns\n3. Usage examples\n4. Error cases"
  }
}
```

**Use cases:**
- API documentation
- README creation
- Code comments
- User guides

### RESEARCH

Information gathering and analysis:

```json
{
  "research-topic": {
    "type": "RESEARCH",
    "task_prompt": "Research {{topic}}:\n1. Overview\n2. Key concepts\n3. Current state\n4. Resources\n\nFocus: {{focus_areas}}"
  }
}
```

**Use cases:**
- Technology evaluation
- Library research
- Best practices
- Problem investigation

### CUSTOM

Any other task type:

```json
{
  "custom-task": {
    "type": "CUSTOM",
    "task_prompt": "{{instructions}}"
  }
}
```

## Creating Task Templates

### Step 1: Define the Task

Identify:
- What the task accomplishes
- What inputs it needs
- What tools it requires
- What type it belongs to

### Step 2: Write the Prompt

Create clear instructions with parameter placeholders:

```json
{
  "task_prompt": "Review the file at {{file_path}}.\n\nFocus areas:\n{{focus_areas}}\n\nProvide:\n1. Summary\n2. Issues found\n3. Recommendations"
}
```

**Placeholder syntax:** `{{parameter_name}}`

### Step 3: Specify Parameters

Define each parameter:

```json
{
  "parameter_specs": {
    "file_path": {
      "type": "string",
      "required": true,
      "description": "Path to the file to review"
    },
    "focus_areas": {
      "type": "string",
      "required": false,
      "description": "Specific aspects to focus on (optional)"
    }
  }
}
```

**Parameter types:** `string`, `integer`, `boolean`, `array`

### Step 4: List Required Tools

Specify which tools the task needs:

```json
{
  "required_tools": ["filesystem", "git", "search"]
}
```

The system verifies these tools are available before execution.

### Step 5: Add to Configuration

Add the complete template to `config.json`:

```json
{
  "task_templates": {
    "my-task": {
      "name": "My Custom Task",
      "type": "CUSTOM",
      "description": "Does something useful",
      "task_prompt": "...",
      "required_tools": ["..."],
      "parameter_specs": { }
    }
  }
}
```

## Template Examples

### Simple Code Review

```json
{
  "quick-review": {
    "name": "Quick Code Review",
    "type": "CODE_ANALYSIS",
    "description": "Fast code review focusing on obvious issues",
    "task_prompt": "Quickly review {{file_path}} for:\n- Syntax errors\n- Obvious bugs\n- Basic style issues\n\nKeep it brief (5 minutes max).",
    "required_tools": ["filesystem"],
    "parameter_specs": {
      "file_path": {
        "type": "string",
        "required": true,
        "description": "File to review"
      }
    }
  }
}
```

### Feature Implementation

```json
{
  "add-crud-endpoint": {
    "name": "Add CRUD Endpoint",
    "type": "CODE_GENERATION",
    "description": "Create a REST API CRUD endpoint",
    "task_prompt": "Create a CRUD endpoint for {{entity_name}}.\n\nGenerate:\n1. Controller class with @RestController\n2. Service interface and implementation\n3. Repository interface\n4. Request/Response DTOs\n5. Basic validation\n\nFollow Spring Boot conventions. Place in package: {{package_name}}",
    "required_tools": ["filesystem", "git", "search"],
    "parameter_specs": {
      "entity_name": {
        "type": "string",
        "required": true,
        "description": "Name of the entity (e.g., 'User', 'Product')"
      },
      "package_name": {
        "type": "string",
        "required": true,
        "description": "Java package name"
      }
    }
  }
}
```

### Test Generation

```json
{
  "generate-tests": {
    "name": "Generate Unit Tests",
    "type": "CODE_GENERATION",
    "description": "Create unit tests for a class",
    "task_prompt": "Generate unit tests for the class in {{class_file}}.\n\nCreate tests for:\n1. All public methods\n2. Edge cases\n3. Error conditions\n4. Boundary values\n\nUse {{test_framework}} framework. Place tests in {{test_directory}}.",
    "required_tools": ["filesystem", "search"],
    "parameter_specs": {
      "class_file": {
        "type": "string",
        "required": true,
        "description": "Path to class to test"
      },
      "test_framework": {
        "type": "string",
        "required": false,
        "description": "Test framework (default: JUnit5)"
      },
      "test_directory": {
        "type": "string",
        "required": false,
        "description": "Where to place tests (default: src/test/java)"
      }
    }
  }
}
```

### Bug Investigation

```json
{
  "investigate-bug": {
    "name": "Investigate Bug",
    "type": "CODE_ANALYSIS",
    "description": "Analyze and diagnose a bug",
    "task_prompt": "Investigate the following bug:\n\n{{bug_description}}\n\nSteps:\n1. Reproduce the issue ({{reproduction_steps}})\n2. Examine relevant code in {{affected_files}}\n3. Identify root cause\n4. Propose solution\n5. Estimate impact\n\nProvide detailed analysis and fix recommendation.",
    "required_tools": ["filesystem", "git", "search", "shell"],
    "parameter_specs": {
      "bug_description": {
        "type": "string",
        "required": true,
        "description": "Description of the bug"
      },
      "reproduction_steps": {
        "type": "string",
        "required": false,
        "description": "Steps to reproduce (optional)"
      },
      "affected_files": {
        "type": "string",
        "required": false,
        "description": "Files suspected to contain bug (optional)"
      }
    }
  }
}
```

## Best Practices

### 1. Clear, Specific Prompts

**Good:**
```
Review {{file_path}} for:
1. Null pointer exceptions
2. Resource leaks
3. SQL injection vulnerabilities
```

**Bad:**
```
Check {{file_path}} for issues.
```

### 2. Reasonable Defaults

Use optional parameters with sensible defaults:

```
"test_framework": "JUnit5 (default if not specified)"
```

### 3. Descriptive Names

- Template ID: `snake-case-descriptive`
- Display Name: `Title Case Readable`
- Parameters: `lower_case_with_underscores`

### 4. Tool Minimalism

Only require tools actually needed:

```json
// Code review doesn't need git
"required_tools": ["filesystem", "search"]

// Feature implementation might need git
"required_tools": ["filesystem", "git", "shell"]
```

### 5. Validation-Friendly Parameters

Design parameters that can be validated:

```json
{
  "severity": {
    "type": "string",
    "required": true,
    "description": "Bug severity: critical, high, medium, low"
  }
}
```

## Programmatic Task Usage

Tasks can be created and executed programmatically in Java:

```java
// Load template
WorkflowTaskTemplate template = config.taskTemplates().get("code-review");

// Create task with parameters
Map<String, String> params = Map.of(
    "file_path", "src/main/java/UserService.java"
);

WorkflowTask task = WorkflowTaskManager.getInstance()
    .createTaskFromTemplate("code-review", params);

// Execute task
WorkflowTaskManager.getInstance().executeTask(task);
```

## Troubleshooting

### Template Not Found

```
magenta> /task show nonexistent
Error: Task template 'nonexistent' not found. Run '/task list' to see available templates.
```

**Solutions:**
- Check template ID spelling
- Verify template exists in config.json
- Reload config if recently added

### Missing Required Tool

```
Error: Task requires tool 'git' but agent doesn't have it available.
```

**Solutions:**
- Add tool to agent's tools list in config
- Switch to an agent that has the tool
- Remove tool from template's required_tools

### Parameter Validation Failed

```
Error: Required parameter 'file_path' not provided for task 'code-review'.
```

**Solutions:**
- Provide all required parameters
- Check parameter spelling
- Review template with `/task show <id>`

## Advanced: Chaining Tasks

While not directly supported, you can chain tasks by:

1. Running first task
2. Using output as input to next task
3. Archiving context between tasks

```
magenta> Run code review on UserService.java
[Review completed]

magenta> Based on the review findings, refactor UserService.java
[Refactoring completed]

magenta> Generate tests for the refactored code
[Tests generated]
```

## Related Workflows

- **Context Management:** See `context-management.md` for task-specific context
- **Multi-Agent:** See `multi-agent-delegation.md` for task delegation between agents

---

**Pro Tip:** Start with simple templates and evolve them based on usage. Monitor which parameters are actually useful and refine prompts based on agent performance.
