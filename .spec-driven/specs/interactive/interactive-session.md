---
mapping:
  implementation:
    - pom.xml
    - src/main/java/org/specdriven/agent/interactive/InteractiveSession.java
    - src/main/java/org/specdriven/agent/interactive/InteractiveSessionState.java
    - src/main/java/org/specdriven/agent/interactive/InMemoryInteractiveSession.java
    - src/main/java/org/specdriven/agent/interactive/LealoneAgentAdapter.java
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
    - src/main/java/org/specdriven/agent/loop/SequentialMilestoneScheduler.java
    - src/main/java/org/specdriven/agent/question/QuestionRuntime.java
  tests:
    - src/test/java/org/specdriven/agent/interactive/LealoneAgentAdapterTest.java
    - src/test/java/org/specdriven/agent/interactive/InteractiveSessionTest.java
    - src/test/java/org/specdriven/agent/interactive/DefaultCommandParserTest.java
    - src/test/java/org/specdriven/agent/interactive/InteractiveCommandHandlerTest.java
    - src/test/java/org/specdriven/agent/interactive/CommandParsingSessionTest.java
    - src/test/java/org/specdriven/agent/interactive/ParsedCommandTest.java
    - src/test/java/org/specdriven/agent/loop/SequentialMilestoneSchedulerTest.java
---

# Interactive Session

## ADDED Requirements

### Requirement: InteractiveSessionState enum

- The system MUST define `InteractiveSessionState` in `org.specdriven.agent.interactive`
- `InteractiveSessionState` MUST include `NEW`, `ACTIVE`, `CLOSED`, and `ERROR`
- `NEW` MUST mean the session has been created but not started
- `ACTIVE` MUST mean the session is started and able to accept input
- `CLOSED` MUST mean the session has been closed and is no longer interactive
- `ERROR` MUST mean the session encountered a terminal failure and MUST reject later input

### Requirement: InteractiveSession interface

- The system MUST define `InteractiveSession` in `org.specdriven.agent.interactive`
- `InteractiveSession` MUST define `String sessionId()`
- `InteractiveSession` MUST define `InteractiveSessionState state()`
- `InteractiveSession` MUST define `void start()`
- `InteractiveSession` MUST define `void submit(String input)`
- `InteractiveSession` MUST define `List<String> drainOutput()`
- `InteractiveSession` MUST define `void close()`
- `sessionId()` MUST return a stable non-blank identifier for the lifetime of the session
- `start()` MUST transition a session from `NEW` to `ACTIVE`
- `start()` MUST reject invocation when the session state is not `NEW`
- `submit(input)` MUST reject null or blank input
- `submit(input)` MUST reject invocation unless the session state is `ACTIVE`
- `drainOutput()` MUST return the currently available session output in emission order
- `drainOutput()` MUST clear the returned output from the pending buffer before returning
- `drainOutput()` MUST return an empty list when no output is pending
- `close()` MUST transition `NEW`, `ACTIVE`, or `ERROR` sessions to `CLOSED`
- `close()` MUST be idempotent when the session state is already `CLOSED`

#### Scenario: Newly created session is not active yet

- GIVEN a newly created `InteractiveSession`
- WHEN its state is queried before `start()`
- THEN `state()` MUST return `NEW`

#### Scenario: Start activates the session

- GIVEN a newly created `InteractiveSession`
- WHEN `start()` is called
- THEN `state()` MUST return `ACTIVE`

#### Scenario: Submit before start is rejected

- GIVEN an `InteractiveSession` whose state is `NEW`
- WHEN `submit("SHOW STATUS")` is called
- THEN the session MUST reject the call
- AND `state()` MUST remain `NEW`

#### Scenario: Blank input is rejected

- GIVEN an `InteractiveSession` whose state is `ACTIVE`
- WHEN `submit("")` or `submit("   ")` is called
- THEN the session MUST reject the call

#### Scenario: Drain output returns ordered snapshot and clears buffer

- GIVEN an `InteractiveSession` whose pending output contains multiple messages in a known order
- WHEN `drainOutput()` is called
- THEN it MUST return those messages in the same order
- AND a second immediate `drainOutput()` call MUST return an empty list

#### Scenario: Closed session rejects later input

- GIVEN an `InteractiveSession` that has been closed
- WHEN `submit("SHOW STATUS")` is called
- THEN the session MUST reject the call
- AND `state()` MUST remain `CLOSED`

#### Scenario: Close is idempotent

- GIVEN an `InteractiveSession` whose state is `CLOSED`
- WHEN `close()` is called again
- THEN no exception MUST be thrown
- AND `state()` MUST remain `CLOSED`

### Requirement: Lealone-backed interactive session adapter

The system MUST provide a Lealone-backed `InteractiveSession` implementation for SQL and natural-language interactive input.

#### Scenario: Adapter starts an interactive Lealone session

- GIVEN a newly created Lealone-backed interactive session
- WHEN `start()` is called
- THEN `state()` MUST return `ACTIVE`
- AND `sessionId()` MUST return a stable non-blank identifier

#### Scenario: Adapter enters error state after start failure

- GIVEN a newly created Lealone-backed interactive session
- WHEN the configured Lealone execution surface cannot be opened
- THEN `start()` MUST be rejected
- AND `state()` MUST return `ERROR`

#### Scenario: Adapter submits input to Lealone execution

- GIVEN a Lealone-backed interactive session in `ACTIVE` state
- WHEN `submit("SHOW SERVICES")` is called
- THEN the input MUST be submitted to the configured Lealone execution surface
- AND any produced output MUST become available through `drainOutput()` in emission order

#### Scenario: Adapter preserves drain semantics

- GIVEN a Lealone-backed interactive session has produced one or more output messages
- WHEN `drainOutput()` is called
- THEN the returned messages MUST be ordered by emission
- AND a second immediate `drainOutput()` call MUST return an empty list

#### Scenario: Adapter closes Lealone resources

- GIVEN a Lealone-backed interactive session in `ACTIVE` state
- WHEN `close()` is called
- THEN the session MUST release its Lealone execution resource
- AND `state()` MUST return `CLOSED`
- AND later `submit("SHOW STATUS")` MUST be rejected

#### Scenario: Adapter enters error state after execution failure

- GIVEN a Lealone-backed interactive session in `ACTIVE` state
- WHEN the configured Lealone execution surface fails while handling submitted input
- THEN `state()` MUST return `ERROR`
- AND later input submission MUST be rejected
- AND pending output, if any, MUST remain available through `drainOutput()`

### Requirement: Lealone adapter does not route answers

The Lealone-backed adapter MUST NOT submit answers to waiting questions or resume loop execution by itself.

#### Scenario: Adapter remains a session surface only

- GIVEN a Lealone-backed interactive session is used to submit input
- WHEN the input is accepted by the adapter
- THEN the adapter MUST limit its observable responsibility to interactive session output and lifecycle state
- AND Question/Answer routing MUST remain governed by the existing question resolution components
- AND loop pause/resume behavior MUST remain governed by the existing loop components

## ADDED Requirements — interactive-command-parser

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
- Constructor MUST accept `QuestionRuntime` and `InMemoryInteractiveSession`
- MUST define `void handle(ParsedCommand command, Question waitingQuestion)`
- For `AnswerCommand`:
  - MUST construct an `Answer` with `source=HUMAN_INLINE`, `decision=ANSWER_ACCEPTED`, `deliveryMode=PAUSE_WAIT_HUMAN`, `confidence=1.0`, `basisSummary="Interactive human reply"`, `sourceRef="InteractiveCommandHandler"`, `answeredAt=System.currentTimeMillis()`
  - MUST call `QuestionRuntime.submitAnswer(waitingQuestion.sessionId(), waitingQuestion.questionId(), answer)`
  - MUST NOT close the session after answer submission (the user may continue interacting)
- For `ShowCommand`:
  - MUST format a human-readable summary and enqueue it to the session output via `session` (delegating to internal query methods)
  - `ShowType.SERVICES` MUST report the services, capabilities, or subsystems relevant to the paused human-in-the-loop session
  - `ShowType.SERVICES` MUST NOT degrade into a placeholder string once the interactive command handler is configured for normal use
  - `ShowType.STATUS` MUST report the current waiting-question and interactive session status available to the command handler without changing loop control state
  - `ShowType.STATUS` MUST NOT degrade into a placeholder string once the interactive command handler is configured for normal use
  - `ShowType.ROADMAP` MUST report roadmap progress derived from the repository's on-disk roadmap files using the canonical milestone and planned-change structure
  - `ShowType.ROADMAP` MUST NOT degrade into a placeholder string once the interactive command handler is configured for normal use
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

#### Scenario: SHOW SERVICES reports paused-session capabilities

- GIVEN an active interactive session with a waiting human-handled question
- WHEN `handle(ShowCommand(SERVICES), question)` is called
- THEN the output available through `session.drainOutput()` MUST describe the services, capabilities, or subsystems relevant to that paused session
- AND the output MUST be non-blank
- AND the output MUST NOT equal a placeholder-only message

#### Scenario: SHOW STATUS reports waiting question context

- GIVEN an active interactive session with a waiting question
- WHEN `handle(ShowCommand(STATUS), question)` is called
- THEN the output available through `session.drainOutput()` MUST describe the waiting question or interactive status context
- AND the output MUST be non-blank
- AND the output MUST NOT equal a placeholder-only message

#### Scenario: SHOW ROADMAP reports roadmap progress from disk

- GIVEN an interactive command handler configured against a repository roadmap
- WHEN `handle(ShowCommand(ROADMAP), question)` is called
- THEN the output available through `session.drainOutput()` MUST summarize roadmap progress using milestone and planned-change data from disk
- AND the output MUST be non-blank
- AND the output MUST NOT equal a placeholder-only message

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
