# interactive-command-parser

## What

Implement a restricted command parser that maps user input from an interactive session (NL or SQL) to bounded actions bound to the waiting Question: answer submission, SHOW queries, and session control. The parser sits between the raw `InteractiveSession.submit()` input and the Question/Answer lifecycle, translating free-form text into typed commands that the system can act on without ambiguity.

## Why

M29's first three changes established the interactive session contract, the Lealone adapter, and the loop-to-interactive bridge. However, the bridge currently only opens an interactive session and waits for it to close — it has no way to interpret what the user typed. Without a command parser, the interactive session surface is a passthrough pipe with no semantic understanding of user input.

The command parser closes this gap: it gives the interactive session the ability to recognize answer submissions, status queries, and session control actions, routing each to the correct subsystem (QuestionRuntime, Store queries, or session lifecycle) while remaining within the bounded action set defined by the milestone scope.

## Scope

- `ParsedCommand` sealed type hierarchy: `AnswerCommand`, `ShowCommand`, `HelpCommand`, `ExitCommand`, `UnknownCommand`
- `CommandParser` interface and `DefaultCommandParser` implementation using pattern matching on raw input
- `InteractiveCommandHandler` that wires parsed commands to the correct subsystem (QuestionRuntime for answers, Store queries for SHOW, session lifecycle for EXIT)
- Integration into the existing `InteractiveSession` flow via a decorator or callback that intercepts `submit()` input, parses it, and dispatches
- Unit tests covering all command types, edge cases (blank, ambiguous, multi-line), and rejection of unsupported commands

## Unchanged Behavior

- `InteractiveSession` interface contract (start/submit/drainOutput/close) remains unchanged
- `LealoneAgentAdapter` SQL execution behavior remains unchanged
- `DefaultLoopDriver` interactive bridge entry/exit logic remains unchanged
- `Question`/`Answer` lifecycle, routing, and persistence remain unchanged
- `LoopDriver.pause()`/`resume()`/`stop()` semantics remain unchanged
