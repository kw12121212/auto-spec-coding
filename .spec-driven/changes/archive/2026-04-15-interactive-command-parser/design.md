# Design: interactive-command-parser

## Approach

Introduce a command parsing layer as a decorator over `InteractiveSession`. When `submit(input)` is called on the decorated session, the input is first passed through `CommandParser.parse(input)`, which returns a `ParsedCommand`. A separate `InteractiveCommandHandler` dispatches the command to the appropriate subsystem:

1. **AnswerCommand** → construct an `Answer` with `source=HUMAN_INLINE`, submit via `QuestionRuntime.submitAnswer()`
2. **ShowCommand** → query the relevant Store, format output, enqueue into session output buffer
3. **HelpCommand** → format available commands list, enqueue into session output buffer
4. **ExitCommand** → close the interactive session
5. **UnknownCommand** → enqueue an error message into session output buffer

The parser uses case-insensitive prefix matching and regex patterns. SQL statements (starting with `SELECT`, `SHOW`, `INSERT` for answers) are detected and routed accordingly. NL input that doesn't match any known pattern falls through to `UnknownCommand`.

### Command Grammar

```
ANSWER <text>           → AnswerCommand(text)
YES / Y / OK / CONFIRM  → AnswerCommand("yes")  (shorthand for affirmative answers)
NO / N / DENY / REJECT  → AnswerCommand("no")   (shorthand for negative answers)
SHOW SERVICES           → ShowCommand(SERVICES)
SHOW STATUS             → ShowCommand(STATUS)
SHOW ROADMAP            → ShowCommand(ROADMAP)
HELP                    → HelpCommand
EXIT / QUIT / BYE       → ExitCommand
<unrecognized input>    → UnknownCommand(original)
```

## Key Decisions

1. **Parser as decorator, not inside InteractiveSession** — Keeps the `InteractiveSession` interface clean; parsing is an orthogonal concern that layers on top of the existing contract. The `InMemoryInteractiveSession` and `LealoneAgentAdapter` remain unchanged.

2. **Sealed ParsedCommand hierarchy** — Enables exhaustive pattern matching in the handler and makes the bounded command set explicit in the type system. New command types can only be added by extending the sealed interface.

3. **HUMAN_INLINE as answer source** — Answers submitted through the interactive command parser use `AnswerSource.HUMAN_INLINE`, distinguishing them from mobile (`HUMAN_MOBILE`) and AI (`AI_AGENT`) sources.

4. **Handler depends on QuestionRuntime, not on LoopDriver** — The handler receives a `QuestionRuntime` reference to submit answers, keeping it decoupled from loop internals. It does not call `LoopDriver.resume()`; loop resume remains the responsibility of the external caller after the session closes.

5. **Case-insensitive prefix matching** — Commands are matched by case-insensitive prefix to tolerate natural variations in user input without requiring an LLM call.

## Alternatives Considered

1. **LLM-based command parsing** — Use an LLM call to interpret user intent. Rejected because: adds latency and cost to every input, introduces non-determinism, and the bounded command set is small enough for deterministic pattern matching.

2. **SQL-only interface** — Require all commands to be valid SQL. Rejected because: the milestone explicitly includes NL input, and forcing SQL would exclude non-technical operators.

3. **Parser inside LealoneAgentAdapter** — Embed command parsing directly into the Lealone adapter. Rejected because: command parsing is session-surface-agnostic (it should work with any `InteractiveSession` implementation, not just the Lealone one).
