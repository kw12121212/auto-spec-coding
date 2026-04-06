# AGENTS.md

Guidelines for AI coding agents working on this project.

## Project Overview

This is a Java SDK reimplementation of spec-coding-sdk (Go), which itself reimplements claw-code (Rust coding agent harness). It exposes coding agent capabilities through three interface layers: native Java SDK, JSON-RPC, and HTTP REST API. Built on Lealone framework as the primary dependency, leveraging its embedded database, HTTP server, scheduling, and async networking. The project uses a spec-driven development workflow managed under `.spec-driven/`.

## Essential Reading

Before making any changes, read these files:

1. `.spec-driven/specs/INDEX.md` — current spec index
2. `.spec-driven/config.yaml` — project context and rules
3. `.spec-driven/roadmap/INDEX.md` — milestone plan and progress

## Development Rules

- **Spec-first**: Only implement what is described in specs. If scope needs to expand, update specs first via `/spec-driven-modify`.
- **Observable behavior**: Specs describe observable behavior only. Tests verify behavior, not implementation details.
- **No speculative code**: Implement only what the current task requires (YAGNI). No abstractions for hypothetical future needs.
- **Read before modify**: Always read existing code before modifying it.
- **Lealone-first**: Use Lealone's built-in capabilities (DB, HTTP server, scheduling, async net) before reaching for external dependencies. Minimize external dependencies.
- **Test requirements**: Every change must include tests (JUnit 5 unit tests minimum). Each test must be independent — no shared mutable state. Prefer real dependencies over mocks for code the project owns.
- **MUST/SHOULD/MAY**: Respect requirement strength. MUST = required, SHOULD = default unless justified, MAY = optional.

## Code Style

- Follow standard Java conventions (checkstyle)
- Maven project at repository root
- JDK 25+ required (VirtualThread support)
- Package structure mirrors module boundaries

## Workflow

Use spec-driven skills for all changes:

- `/spec-driven-propose` — propose a new change
- `/spec-driven-apply` — implement tasks
- `/spec-driven-verify` — verify completion
- `/spec-driven-review` — code quality review
- `/spec-driven-archive` — archive completed change

## Language

Specs and roadmap are written in Chinese. Code, comments, and commit messages should be in English.
