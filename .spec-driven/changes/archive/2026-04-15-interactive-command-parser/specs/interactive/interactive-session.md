---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/interactive/CommandParser.java
    - src/main/java/org/specdriven/agent/interactive/DefaultCommandParser.java
    - src/main/java/org/specdriven/agent/interactive/ParsedCommand.java
    - src/main/java/org/specdriven/agent/interactive/AnswerCommand.java
    - src/main/java/org/specdriven/agent/interactive/ShowCommand.java
    - src/main/java/org/specdriven/agent/interactive/HelpCommand.java
    - src/main/java/org/specdriven/agent/interactive/ExitCommand.java
    - src/main/java/org/specdriven/agent/interactive/UnknownCommand.java
    - src/main/java/org/specdriven/agent/interactive/ShowType.java
    - src/main/java/org/specdriven/agent/interactive/InteractiveCommandHandler.java
    - src/main/java/org/specdriven/agent/interactive/CommandParsingSession.java
  tests:
    - src/test/java/org/specdriven/agent/interactive/DefaultCommandParserTest.java
    - src/test/java/org/specdriven/agent/interactive/InteractiveCommandHandlerTest.java
    - src/test/java/org/specdriven/agent/interactive/CommandParsingSessionTest.java
    - src/test/java/org/specdriven/agent/interactive/ParsedCommandTest.java
---

## ADDED Requirements

### Requirement: ParsedCommand sealed interface

- The system MUST define `ParsedCommand` as a sealed interface in `org.specdriven.agent.interactive`
- Permitted subtypes MUST be `AnswerCommand`, `ShowCommand`, `HelpCommand`, `ExitCommand`, and `UnknownCommand`
- Each subtype MUST expose `originalInput()` returning the raw input string that was parsed

### Requirement: AnswerCommand record

- The system MUST define `AnswerCommand` as a record implementing `ParsedCommand` in `org.specdriven.agent.interactive`
- MUST expose `answerText()` (String) — the extracted answer content
- `answerText()` MUST be non-blank
- `originalInput()` MUST return the full raw input that produced this command

#### Scenario: Explicit ANSWER prefix

- GIVEN raw input "ANSWER use the cached version"
- WHEN parsed by the command parser
- THEN the result MUST be an `AnswerCommand` with `answerText` = "use the cached version"

#### Scenario: Affirmative shorthand

- GIVEN raw input "yes"
- WHEN parsed by the command parser
- THEN the result MUST be an `AnswerCommand` with `answerText` = "yes"

#### Scenario: Negative shorthand

- GIVEN raw input "no"
- WHEN parsed by the command parser
- THEN the result MUST be an `AnswerCommand` with `answerText` = "no"

### Requirement: ShowCommand record

- The system MUST define `ShowCommand` as a record implementing `ParsedCommand` in `org.specdriven.agent.interactive`
- MUST expose `showType()` returning `ShowType`
- `ShowType` MUST be an enum with values `SERVICES`, `STATUS`, `ROADMAP`

#### Scenario: SHOW SERVICES

- GIVEN raw input "SHOW SERVICES"
- WHEN parsed by the command parser
- THEN the result MUST be a `ShowCommand` with `showType` = `SERVICES`

#### Scenario: SHOW STATUS

- GIVEN raw input "show status"
- WHEN parsed by the command parser
- THEN the result MUST be a `ShowCommand` with `showType` = `STATUS`

#### Scenario: SHOW ROADMAP

- GIVEN raw input "Show Roadmap"
- WHEN parsed by the command parser
- THEN the result MUST be a `ShowCommand` with `showType` = `ROADMAP`

### Requirement: HelpCommand record

- The system MUST define `HelpCommand` as a record implementing `ParsedCommand` in `org.specdriven.agent.interactive`

#### Scenario: HELP input

- GIVEN raw input "help"
- WHEN parsed by the command parser
- THEN the result MUST be a `HelpCommand`

### Requirement: ExitCommand record

- The system MUST define `ExitCommand` as a record implementing `ParsedCommand` in `org.specdriven.agent.interactive`

#### Scenario: EXIT input

- GIVEN raw input "exit"
- WHEN parsed by the command parser
- THEN the result MUST be an `ExitCommand`

#### Scenario: QUIT alias

- GIVEN raw input "quit"
- WHEN parsed by the command parser
- THEN the result MUST be an `ExitCommand`

### Requirement: UnknownCommand record

- The system MUST define `UnknownCommand` as a record implementing `ParsedCommand` in `org.specdriven.agent.interactive`

#### Scenario: Unrecognized input

- GIVEN raw input "do something random"
- WHEN parsed by the command parser
- THEN the result MUST be an `UnknownCommand` with `originalInput` = "do something random"

### Requirement: CommandParser interface

- The system MUST define `CommandParser` as a public interface in `org.specdriven.agent.interactive`
- MUST define `ParsedCommand parse(String input)`
- MUST reject null or blank input by throwing `IllegalArgumentException`
- MUST NOT throw for any non-blank input — unrecognized input MUST produce `UnknownCommand`

### Requirement: DefaultCommandParser

- The system MUST provide `DefaultCommandParser` implementing `CommandParser` in `org.specdriven.agent.interactive`
- Command recognition MUST be case-insensitive
- MUST recognize the following command patterns:
  - `ANSWER <text>` → `AnswerCommand` with the trailing text as `answerText`
  - `YES`, `Y`, `OK`, `CONFIRM` (standalone) → `AnswerCommand` with `answerText` = original input
  - `NO`, `N`, `DENY`, `REJECT` (standalone) → `AnswerCommand` with `answerText` = original input
  - `SHOW SERVICES` → `ShowCommand(SERVICES)`
  - `SHOW STATUS` → `ShowCommand(STATUS)`
  - `SHOW ROADMAP` → `ShowCommand(ROADMAP)`
  - `HELP` → `HelpCommand`
  - `EXIT`, `QUIT`, `BYE` → `ExitCommand`
- Any non-blank input not matching the above MUST produce `UnknownCommand`

#### Scenario: Case-insensitive matching

- GIVEN raw input "Show services"
- WHEN parsed
- THEN the result MUST be a `ShowCommand` with `showType` = `SERVICES`

#### Scenario: Blank input rejection

- GIVEN raw input "   "
- WHEN `parse()` is called
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: Leading/trailing whitespace trimming

- GIVEN raw input "  HELP  "
- WHEN parsed
- THEN the result MUST be a `HelpCommand`

### Requirement: InteractiveCommandHandler

- The system MUST provide `InteractiveCommandHandler` in `org.specdriven.agent.interactive`
- Constructor MUST accept `QuestionRuntime` and `InteractiveSession`
- MUST define `void handle(ParsedCommand command, Question waitingQuestion)`
- For `AnswerCommand`:
  - MUST construct an `Answer` with `source=HUMAN_INLINE`, `decision=ANSWER_ACCEPTED`, `deliveryMode=PAUSE_WAIT_HUMAN`, `confidence=1.0`, `basisSummary="Interactive human reply"`, `sourceRef="InteractiveCommandHandler"`, `answeredAt=System.currentTimeMillis()`
  - MUST call `QuestionRuntime.submitAnswer(waitingQuestion.sessionId(), waitingQuestion.questionId(), answer)`
  - MUST NOT close the session after answer submission (the user may continue interacting)
- For `ShowCommand`:
  - MUST format a human-readable status summary and enqueue it to the session output via `session` (delegating to internal query methods)
  - `ShowType.SERVICES` MUST format currently registered services
  - `ShowType.STATUS` MUST format the current loop and question status
  - `ShowType.ROADMAP` MUST format the current roadmap progress summary
- For `HelpCommand`:
  - MUST format a list of available commands with brief descriptions and enqueue it to the session output
- For `ExitCommand`:
  - MUST call `session.close()`
- For `UnknownCommand`:
  - MUST enqueue a message indicating the input was not recognized, with a suggestion to type HELP

#### Scenario: Answer submission through handler

- GIVEN an `InteractiveCommandHandler` with a `QuestionRuntime` and an `InteractiveSession`
- AND a waiting `Question` in `WAITING_FOR_ANSWER` state
- WHEN `handle(AnswerCommand, question)` is called
- THEN an `Answer` with `source=HUMAN_INLINE` MUST be submitted to the `QuestionRuntime`
- AND the session state MUST remain `ACTIVE`

#### Scenario: Exit closes session

- GIVEN an `InteractiveCommandHandler` with an active `InteractiveSession`
- WHEN `handle(ExitCommand, question)` is called
- THEN the session MUST be closed

#### Scenario: Unknown command produces guidance

- GIVEN an `InteractiveCommandHandler` with an active `InteractiveSession`
- WHEN `handle(UnknownCommand, question)` is called
- THEN a guidance message MUST be available via `session.drainOutput()`
- AND the session state MUST remain `ACTIVE`

### Requirement: CommandParsingSession decorator

- The system MUST provide `CommandParsingSession` implementing `InteractiveSession` in `org.specdriven.agent.interactive`
- Constructor MUST accept `InteractiveSession delegate`, `CommandParser parser`, `InteractiveCommandHandler handler`, and `Question waitingQuestion`
- `start()` MUST delegate to the underlying session
- `submit(input)` MUST:
  1. Call `parser.parse(input)`
  2. Call `handler.handle(parsedCommand, waitingQuestion)`
  3. If parsing throws, enqueue the exception message as output
- `drainOutput()` MUST delegate to the underlying session
- `close()` MUST delegate to the underlying session
- `state()` MUST delegate to the underlying session
- `sessionId()` MUST delegate to the underlying session

#### Scenario: Decorated session delegates lifecycle

- GIVEN a `CommandParsingSession` wrapping an `InMemoryInteractiveSession`
- WHEN `start()` is called
- THEN the delegate session state MUST become `ACTIVE`

#### Scenario: Decorated session parses and dispatches

- GIVEN a `CommandParsingSession` in `ACTIVE` state with a waiting question
- WHEN `submit("HELP")` is called
- THEN a help message MUST be available via `drainOutput()`

#### Scenario: Parse error produces output, not exception

- GIVEN a `CommandParsingSession` in `ACTIVE` state
- WHEN `submit("")` is called
- THEN no exception MUST propagate
- AND an error message MUST be available via `drainOutput()`

### Requirement: Interactive session answer does not bypass Question lifecycle

- Answers submitted through the interactive command parser MUST use the same `QuestionRuntime.submitAnswer()` path as all other answer sources
- The interactive command handler MUST NOT directly modify `Question.status`
- The interactive command handler MUST NOT directly resume the `LoopDriver`

#### Scenario: Interactive answer goes through QuestionRuntime

- GIVEN an interactive session with a waiting question
- WHEN the user submits an answer via the command parser
- THEN the answer MUST be submitted via `QuestionRuntime.submitAnswer(sessionId, questionId, answer)`
- AND the `Question` status transition MUST be governed by existing `QuestionRuntime` logic
