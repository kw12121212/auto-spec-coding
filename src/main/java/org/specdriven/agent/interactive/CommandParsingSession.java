package org.specdriven.agent.interactive;

import org.specdriven.agent.question.Question;

import java.util.List;
import java.util.Objects;

/**
 * Decorator over {@link InteractiveSession} that intercepts {@code submit()} input,
 * parses it through a {@link CommandParser}, and dispatches via an
 * {@link InteractiveCommandHandler}.
 *
 * <p>If parsing throws, the exception message is enqueued as session output
 * instead of propagating to the caller.
 */
public final class CommandParsingSession implements InteractiveSession {

    private final InteractiveSession delegate;
    private final CommandParser parser;
    private final InteractiveCommandHandler handler;
    private final Question waitingQuestion;

    public CommandParsingSession(InteractiveSession delegate,
                                 CommandParser parser,
                                 InteractiveCommandHandler handler,
                                 Question waitingQuestion) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.handler = Objects.requireNonNull(handler, "handler");
        // waitingQuestion may be null for sessions without a pending question
        this.waitingQuestion = waitingQuestion;
    }

    @Override
    public String sessionId() {
        return delegate.sessionId();
    }

    @Override
    public InteractiveSessionState state() {
        return delegate.state();
    }

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void submit(String input) {
        try {
            ParsedCommand command = parser.parse(input);
            handler.handle(command, waitingQuestion);
        } catch (Exception e) {
            handler.enqueueError(e.getMessage());
        }
    }

    @Override
    public List<String> drainOutput() {
        return delegate.drainOutput();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
