package org.specdriven.agent.interactive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultCommandParserTest {

    private final DefaultCommandParser parser = new DefaultCommandParser();

    // --- AnswerCommand: explicit ANSWER prefix ---

    @Test
    void answerPrefixExtractsText() {
        ParsedCommand cmd = parser.parse("ANSWER use the cached version");
        assertInstanceOf(AnswerCommand.class, cmd);
        assertEquals("use the cached version", ((AnswerCommand) cmd).answerText());
        assertEquals("ANSWER use the cached version", cmd.originalInput());
    }

    @Test
    void answerPrefixCaseInsensitive() {
        ParsedCommand cmd = parser.parse("answer yes do it");
        assertInstanceOf(AnswerCommand.class, cmd);
        assertEquals("yes do it", ((AnswerCommand) cmd).answerText());
    }

    // --- AnswerCommand: affirmative shorthand ---

    @Test
    void yesIsAnswer() {
        ParsedCommand cmd = parser.parse("yes");
        assertInstanceOf(AnswerCommand.class, cmd);
        assertEquals("yes", ((AnswerCommand) cmd).answerText());
    }

    @Test
    void yIsAnswer() {
        ParsedCommand cmd = parser.parse("Y");
        assertInstanceOf(AnswerCommand.class, cmd);
        assertEquals("Y", ((AnswerCommand) cmd).answerText());
    }

    @Test
    void okIsAnswer() {
        ParsedCommand cmd = parser.parse("ok");
        assertInstanceOf(AnswerCommand.class, cmd);
        assertEquals("ok", ((AnswerCommand) cmd).answerText());
    }

    @Test
    void confirmIsAnswer() {
        ParsedCommand cmd = parser.parse("CONFIRM");
        assertInstanceOf(AnswerCommand.class, cmd);
        assertEquals("CONFIRM", ((AnswerCommand) cmd).answerText());
    }

    // --- AnswerCommand: negative shorthand ---

    @Test
    void noIsAnswer() {
        ParsedCommand cmd = parser.parse("no");
        assertInstanceOf(AnswerCommand.class, cmd);
        assertEquals("no", ((AnswerCommand) cmd).answerText());
    }

    @Test
    void nIsAnswer() {
        ParsedCommand cmd = parser.parse("N");
        assertInstanceOf(AnswerCommand.class, cmd);
        assertEquals("N", ((AnswerCommand) cmd).answerText());
    }

    @Test
    void denyIsAnswer() {
        ParsedCommand cmd = parser.parse("deny");
        assertInstanceOf(AnswerCommand.class, cmd);
        assertEquals("deny", ((AnswerCommand) cmd).answerText());
    }

    @Test
    void rejectIsAnswer() {
        ParsedCommand cmd = parser.parse("REJECT");
        assertInstanceOf(AnswerCommand.class, cmd);
        assertEquals("REJECT", ((AnswerCommand) cmd).answerText());
    }

    // --- ShowCommand ---

    @Test
    void showServices() {
        ParsedCommand cmd = parser.parse("SHOW SERVICES");
        assertInstanceOf(ShowCommand.class, cmd);
        assertEquals(ShowType.SERVICES, ((ShowCommand) cmd).showType());
    }

    @Test
    void showStatusCaseInsensitive() {
        ParsedCommand cmd = parser.parse("show status");
        assertInstanceOf(ShowCommand.class, cmd);
        assertEquals(ShowType.STATUS, ((ShowCommand) cmd).showType());
    }

    @Test
    void showRoadmapMixedCase() {
        ParsedCommand cmd = parser.parse("Show Roadmap");
        assertInstanceOf(ShowCommand.class, cmd);
        assertEquals(ShowType.ROADMAP, ((ShowCommand) cmd).showType());
    }

    @Test
    void showUnknownTypeIsUnknown() {
        ParsedCommand cmd = parser.parse("SHOW UNKNOWN");
        assertInstanceOf(UnknownCommand.class, cmd);
    }

    @Test
    void showWithoutTypeIsUnknown() {
        ParsedCommand cmd = parser.parse("SHOW");
        assertInstanceOf(UnknownCommand.class, cmd);
    }

    // --- HelpCommand ---

    @Test
    void helpIsHelp() {
        ParsedCommand cmd = parser.parse("help");
        assertInstanceOf(HelpCommand.class, cmd);
    }

    @Test
    void helpUpperCase() {
        ParsedCommand cmd = parser.parse("HELP");
        assertInstanceOf(HelpCommand.class, cmd);
    }

    // --- ExitCommand ---

    @Test
    void exitIsExit() {
        ParsedCommand cmd = parser.parse("exit");
        assertInstanceOf(ExitCommand.class, cmd);
    }

    @Test
    void quitIsExit() {
        ParsedCommand cmd = parser.parse("quit");
        assertInstanceOf(ExitCommand.class, cmd);
    }

    @Test
    void byeIsExit() {
        ParsedCommand cmd = parser.parse("BYE");
        assertInstanceOf(ExitCommand.class, cmd);
    }

    // --- UnknownCommand ---

    @Test
    void unrecognizedIsUnknown() {
        ParsedCommand cmd = parser.parse("do something random");
        assertInstanceOf(UnknownCommand.class, cmd);
        assertEquals("do something random", cmd.originalInput());
    }

    @Test
    void answerWithNoTextIsUnknown() {
        ParsedCommand cmd = parser.parse("ANSWER   ");
        assertInstanceOf(UnknownCommand.class, cmd);
    }

    // --- Whitespace trimming ---

    @Test
    void leadingTrailingWhitespaceTrimmed() {
        ParsedCommand cmd = parser.parse("  HELP  ");
        assertInstanceOf(HelpCommand.class, cmd);
    }

    // --- Blank/null rejection ---

    @Test
    void blankInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("   "));
    }

    @Test
    void nullInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
    }

    @Test
    void emptyInputThrows() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(""));
    }
}
