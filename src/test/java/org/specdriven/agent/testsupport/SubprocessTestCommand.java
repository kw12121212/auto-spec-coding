package org.specdriven.agent.testsupport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SubprocessTestCommand {

    private SubprocessTestCommand() {
    }

    public static String javaCommand(Class<?> mainClass, String... args) {
        return String.join(" ", javaCommandParts(mainClass, args));
    }

    public static String shellSafeJavaCommand(Class<?> mainClass, String... args) {
        return javaCommandParts(mainClass, args).stream()
                .map(SubprocessTestCommand::shellQuote)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    public static List<String> javaCommandList(Class<?> mainClass, String... args) {
        return List.copyOf(javaCommandParts(mainClass, args));
    }

    private static List<String> javaCommandParts(Class<?> mainClass, String... args) {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(mainClass.getName());
        command.addAll(Arrays.asList(args));
        return command;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
