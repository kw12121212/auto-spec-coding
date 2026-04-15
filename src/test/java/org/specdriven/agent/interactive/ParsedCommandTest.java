package org.specdriven.agent.interactive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParsedCommandTest {

    // --- AnswerCommand ---

    @Test
    void answerCommandConstruction() {
        AnswerCommand cmd = new AnswerCommand("yes", "yes");
        assertEquals("yes", cmd.answerText());
        assertEquals("yes", cmd.originalInput());
    }

    @Test
    void answerCommandRejectsBlankAnswerText() {
        assertThrows(IllegalArgumentException.class, () -> new AnswerCommand("ANSWER  ", "  "));
    }

    @Test
    void answerCommandRejectsNullAnswerText() {
        assertThrows(IllegalArgumentException.class, () -> new AnswerCommand("input", null));
    }

    @Test
    void answerCommandRejectsNullOriginalInput() {
        assertThrows(NullPointerException.class, () -> new AnswerCommand(null, "text"));
    }

    // --- ShowCommand ---

    @Test
    void showCommandConstruction() {
        ShowCommand cmd = new ShowCommand("SHOW STATUS", ShowType.STATUS);
        assertEquals(ShowType.STATUS, cmd.showType());
        assertEquals("SHOW STATUS", cmd.originalInput());
    }

    @Test
    void showCommandRejectsNullShowType() {
        assertThrows(NullPointerException.class, () -> new ShowCommand("input", null));
    }

    // --- HelpCommand ---

    @Test
    void helpCommandConstruction() {
        HelpCommand cmd = new HelpCommand("HELP");
        assertEquals("HELP", cmd.originalInput());
    }

    @Test
    void helpCommandRejectsNullInput() {
        assertThrows(NullPointerException.class, () -> new HelpCommand(null));
    }

    // --- ExitCommand ---

    @Test
    void exitCommandConstruction() {
        ExitCommand cmd = new ExitCommand("exit");
        assertEquals("exit", cmd.originalInput());
    }

    @Test
    void exitCommandRejectsNullInput() {
        assertThrows(NullPointerException.class, () -> new ExitCommand(null));
    }

    // --- UnknownCommand ---

    @Test
    void unknownCommandConstruction() {
        UnknownCommand cmd = new UnknownCommand("foo");
        assertEquals("foo", cmd.originalInput());
    }

    @Test
    void unknownCommandRejectsNullInput() {
        assertThrows(NullPointerException.class, () -> new UnknownCommand(null));
    }

    // --- Sealed interface coverage ---

    @Test
    void allSubtypesImplementParsedCommand() {
        ParsedCommand a = new AnswerCommand("yes", "yes");
        ParsedCommand s = new ShowCommand("SHOW STATUS", ShowType.STATUS);
        ParsedCommand h = new HelpCommand("HELP");
        ParsedCommand e = new ExitCommand("exit");
        ParsedCommand u = new UnknownCommand("foo");

        assertInstanceOf(ParsedCommand.class, a);
        assertInstanceOf(ParsedCommand.class, s);
        assertInstanceOf(ParsedCommand.class, h);
        assertInstanceOf(ParsedCommand.class, e);
        assertInstanceOf(ParsedCommand.class, u);
    }
}
