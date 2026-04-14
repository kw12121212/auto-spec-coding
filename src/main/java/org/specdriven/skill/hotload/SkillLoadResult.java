package org.specdriven.skill.hotload;

import org.specdriven.skill.compiler.SkillCompilationDiagnostic;

import java.util.List;
import java.util.Objects;

public record SkillLoadResult(boolean success, String entryClassName, List<SkillCompilationDiagnostic> diagnostics) {

    public SkillLoadResult {
        entryClassName = Objects.requireNonNull(entryClassName, "entryClassName must not be null");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics must not be null"));
    }
}
