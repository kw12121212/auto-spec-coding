# environment-profile-contract

## What

Define the first project-level environment profile contract for the repository.

This change introduces a named profile configuration model loaded from project YAML,
including profile declaration shape, default-profile requirements, selection
precedence, validation, and observable failure behavior. It establishes the
configuration boundary for later Sandlock execution, toolchain isolation, and
tool execution binding work without yet changing how commands are launched.

## Why

M38 requires profile-based isolated execution, but the repository does not yet
define what a profile is, where it is loaded from, how callers select one, or
how invalid profile references fail. Starting with the contract reduces the risk
that later changes implement incompatible profile semantics across BashTool,
background processes, loop phases, and Sandlock integration.

The accepted planning decisions for this change are:
- The main spec should live under `.spec-driven/specs/config/`
- v1 profile declarations should come only from project YAML
- Profile resolution must always produce a selected profile, at least via the
  configured default profile

## Scope

In scope:
- Define a project YAML contract for named environment profiles
- Define the required default-profile declaration and explicit profile
  selection precedence
- Define the first observable fields for JDK, Node.js, Go, and Python profile
  declarations
- Define validation and diagnostics for missing default profiles, unknown
  profile references, and invalid profile field values
- Expose the effective profile configuration through existing configuration and
  SDK assembly paths when repository context makes those paths knowable

Out of scope:
- Sandlock process launching
- PATH, HOME, cache, and toolchain isolation implementation
- Binding profile selection into BashTool, background processes, or loop phase
  command execution
- Profile-specific permission or audit event behavior
- Automatic installation of language runtimes or toolchains

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing command execution paths continue using the host environment until a
  later M38 change binds execution to profiles
- Existing `platform.*`, LLM, vault, and other YAML configuration behavior
  remains unchanged outside the new profile keys
- Existing `BashTool`, background-process, and loop phase execution semantics do
  not change in this proposal
