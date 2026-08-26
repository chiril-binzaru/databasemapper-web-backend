---
name: implement
description: Implements code changes using an adaptive agent workflow. Use for features, bug fixes, refactoring, and other implementation tasks.
disable-model-invocation: true
argument-hint: "[implementation task]"
---

# Implementation task

$ARGUMENTS

## General rules

1. Read `CLAUDE.md` and any applicable project instructions.
2. Inspect the relevant existing implementation before making changes.
3. Follow established architecture, naming, testing, and error-handling patterns.
4. Reuse analogous implementations where appropriate.
5. Keep the change focused on the requested task.
6. Do not introduce unrelated refactoring.
7. Do not commit, push, merge, deploy, or modify remote resources unless the user explicitly requests it.
8. Ask the user only when an unresolved decision would materially affect behavior, architecture, compatibility, or scope.
9. Give every subagent sufficient context to work independently, including:
   - the original task;
   - relevant requirements and constraints;
   - discovered files and patterns;
   - outputs from preceding subagents;
   - the expected deliverable.

## Choose the workflow depth

Classify the task according to architectural impact, uncertainty, risk, and verification needs. Do not use the number of affected files as the primary complexity criterion.

### Trivial change

Examples:

- an obvious one-line correction;
- a typo or literal replacement;
- a purely mechanical edit with no behavioral uncertainty.

Workflow:

1. Make the change directly without invoking subagents.
2. Run the smallest relevant verification, if one exists.
3. Report the change and verification result.

### Small implementation

Use this workflow when the change:

- is localized and well understood;
- follows an existing implementation pattern;
- introduces no schema, contract, or architectural decisions;
- has low regression risk.

Workflow:

1. Invoke the `coder` subagent to implement the change.
2. Inspect the resulting diff.
3. Run the most relevant targeted verification.
4. If the implementation reveals unexpected complexity or risk, reclassify the task as standard or complex and continue with the corresponding workflow.

Skip separate architect, reviewer, and tester subagents unless the task becomes more complex than initially assessed.

### Standard implementation

Use this workflow when the change:

- affects meaningful application or business logic;
- requires new or updated tests;
- has moderate regression risk;
- is not architecturally novel or high risk.

Workflow:

1. Invoke the `coder` subagent to implement the change and relevant tests.
2. Invoke the `code-reviewer` subagent to inspect the complete diff.
3. Require the reviewer to:
   - remain read-only;
   - identify correctness, regression, security, maintainability, and test-coverage concerns;
   - distinguish blocking findings from optional improvements;
   - support findings with concrete evidence from the code.
4. If the reviewer reports significant findings, invoke the `coder` again with those findings.
5. Re-run focused verification after corrections.
6. Invoke the `tester` subagent to perform targeted and relevant broader verification.

### Complex or high-risk implementation

Use this workflow when the change involves one or more of the following:

- schema or migration changes;
- new architectural or integration patterns;
- multi-datasource behavior;
- public API or data-contract changes;
- security-sensitive logic;
- cross-cutting behavior;
- substantial uncertainty;
- unclear requirements with materially different implementation options;
- high regression risk.

Workflow:

1. Invoke the `code-architect` subagent before modifying code.
2. Require the architect to provide:
   - relevant existing patterns;
   - files to create or modify;
   - implementation sequence;
   - schema or migration implications;
   - compatibility concerns;
   - risks and edge cases;
   - material open decisions.
3. If material open decisions remain, present them to the user and wait for a decision before implementation.
4. Invoke the `coder` subagent with the original task, discovered context, and architectural plan.
5. Invoke the `code-reviewer` subagent to inspect the complete diff.
6. Require the reviewer to remain read-only and prioritize findings by severity.
7. Return significant findings to the `coder`.
8. Invoke the `code-reviewer` once more after corrections.
9. Invoke the `tester` subagent to perform targeted and relevant broader verification.

## Verification requirements

1. Follow the build and test commands documented in `CLAUDE.md` or the project configuration.
2. Run targeted tests before broader test suites.
3. Verify both successful behavior and relevant failure or edge cases.
4. For bug fixes, add or identify a regression test that fails without the fix and passes with it.
5. For schema changes, verify migrations, mappings, constraints, and backward-compatibility implications.
6. Inspect the final diff for:
   - accidental unrelated changes;
   - debugging code;
   - commented-out code;
   - hardcoded temporary values;
   - exposed secrets;
   - missing tests;
   - generated or environment-specific files that should not be committed.
7. Never report a test or command as successful unless it was actually executed successfully.
8. If verification cannot be completed, clearly state:
   - what was not verified;
   - why it could not be verified;
   - what command or action is still required.

## Handling review findings

- Blocking correctness, security, compatibility, or regression findings must be addressed before completion.
- Non-blocking improvements should be reported separately and should not expand the task automatically.
- Perform one correction and re-review cycle by default.
- If significant findings remain after that cycle, report them instead of creating an unbounded agent loop.

## Final response

Finish with:

1. A concise implementation summary.
2. The workflow classification used: trivial, small, standard, or complex.
3. The files created or modified.
4. Tests and verification commands executed, with their results.
5. Important design decisions.
6. Remaining risks, limitations, or unresolved issues.
7. Any recommended follow-up work that was intentionally left outside the task scope.