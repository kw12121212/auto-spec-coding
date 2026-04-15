package org.specdriven.agent.interactive;

import java.util.Map;
import java.util.Set;

/**
 * Default command parser using case-insensitive prefix matching.
 *
 * <p>Recognized commands:
 * <ul>
 *   <li>{@code ANSWER <text>} → {@link AnswerCommand}</li>
 *   <li>{@code YES/Y/OK/CONFIRM} → {@link AnswerCommand} (affirmative shorthand)</li>
 *   <li>{@code NO/N/DENY/REJECT} → {@link AnswerCommand} (negative shorthand)</li>
 *   <li>{@code SHOW SERVICES/STATUS/ROADMAP} → {@link ShowCommand}</li>
 *   <li>{@code HELP} → {@link HelpCommand}</li>
 *   <li>{@code EXIT/QUIT/BYE} → {@link ExitCommand}</li>
 * </ul>
 * All other non-blank input produces {@link UnknownCommand}.
 */
public final class DefaultCommandParser implements CommandParser {

    private static final Set<String> AFFIRMATIVE = Set.of("YES", "Y", "OK", "CONFIRM");
    private static final Set<String> NEGATIVE = Set.of("NO", "N", "DENY", "REJECT");
    private static final Set<String> EXIT_WORDS = Set.of("EXIT", "QUIT", "BYE");
    private static final Map<String, ShowType> SHOW_TYPES = Map.of(
            "SERVICES", ShowType.SERVICES,
            "STATUS", ShowType.STATUS,
            "ROADMAP", ShowType.ROADMAP
    );

    @Override
    public ParsedCommand parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input must not be null or blank");
        }
        String trimmed = input.trim();
        String upper = trimmed.toUpperCase();

        // ANSWER <text>
        if (upper.startsWith("ANSWER ")) {
            String answerText = trimmed.substring(7).trim();
            if (answerText.isBlank()) {
                return new UnknownCommand(trimmed);
            }
            return new AnswerCommand(trimmed, answerText);
        }

        // Single-word commands
        String firstWord = upper.split("\\s+", 2)[0];

        // Affirmative shorthand
        if (AFFIRMATIVE.contains(firstWord) && !upper.contains(" ")) {
            return new AnswerCommand(trimmed, trimmed);
        }

        // Negative shorthand
        if (NEGATIVE.contains(firstWord) && !upper.contains(" ")) {
            return new AnswerCommand(trimmed, trimmed);
        }

        // SHOW <type>
        if (firstWord.equals("SHOW")) {
            String[] parts = upper.split("\\s+", 3);
            if (parts.length >= 2) {
                ShowType showType = SHOW_TYPES.get(parts[1]);
                if (showType != null) {
                    return new ShowCommand(trimmed, showType);
                }
            }
            return new UnknownCommand(trimmed);
        }

        // HELP
        if (firstWord.equals("HELP") && !upper.contains(" ")) {
            return new HelpCommand(trimmed);
        }

        // EXIT / QUIT / BYE
        if (EXIT_WORDS.contains(firstWord) && !upper.contains(" ")) {
            return new ExitCommand(trimmed);
        }

        return new UnknownCommand(trimmed);
    }
}
