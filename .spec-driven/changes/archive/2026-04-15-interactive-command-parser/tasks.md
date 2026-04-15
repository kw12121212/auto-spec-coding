# Tasks: interactive-command-parser

## Implementation

- [x] Define `ParsedCommand` sealed interface in `org.specdriven.agent.interactive` with permitted subtypes
- [x] Implement `AnswerCommand` record with `answerText()` and `originalInput()`
- [x] Implement `ShowCommand` record with `showType()` (`ShowType` enum: SERVICES, STATUS, ROADMAP)
- [x] Implement `HelpCommand` record
- [x] Implement `ExitCommand` record
- [x] Implement `UnknownCommand` record
- [x] Define `CommandParser` interface with `ParsedCommand parse(String input)`
- [x] Implement `DefaultCommandParser` with case-insensitive pattern matching for all command types
- [x] Implement `InteractiveCommandHandler` that dispatches parsed commands to QuestionRuntime, session output, or session close
- [x] Implement `CommandParsingSession` decorator that intercepts `submit()`, parses, and dispatches via handler

## Testing

- [x] Run `mvn compile -pl . -q` for build validation and verify zero compilation errors
- [x] Write and run `DefaultCommandParserTest` unit tests covering all command patterns, case insensitivity, blank rejection, unknown fallback, whitespace trimming
- [x] Write and run `InteractiveCommandHandlerTest` unit tests covering answer submission, SHOW output, HELP output, EXIT close, unknown guidance
- [x] Write and run `CommandParsingSessionTest` unit tests covering lifecycle delegation, parse+dispatch on submit, parse error produces output not exception
- [x] Write and run `ParsedCommandTest` unit tests covering record construction and field access for all subtypes
- [x] Run `mvn test -pl .` and verify all tests pass including existing tests

## Verification

- [x] Verify all command types produce the correct `ParsedCommand` subtype
- [x] Verify `InteractiveCommandHandler` uses `QuestionRuntime.submitAnswer()` for answer commands
- [x] Verify `CommandParsingSession` never throws on `submit()` for any input
- [x] Verify existing `InteractiveSessionTest`, `LealoneAgentAdapterTest`, and `DefaultLoopDriverTest` still pass
- [x] Verify `InteractiveSession` interface is unchanged (no new methods added)
