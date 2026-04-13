package org.specdriven.skill.compiler;

public record SkillCompilationDiagnostic(String message, long lineNumber, long columnNumber) {
}
