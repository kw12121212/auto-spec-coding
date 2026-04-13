package org.specdriven.skill.compiler;

import java.util.List;
import java.util.Objects;

public record SkillCompilationResult(boolean success, String entryClassName, List<SkillCompilationDiagnostic> diagnostics) {

    public SkillCompilationResult {
        entryClassName = Objects.requireNonNull(entryClassName, "entryClassName must not be null");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
    }
}
