package org.specdriven.skill.compiler;

import java.nio.file.Path;

public interface SkillSourceCompiler {

    SkillCompilationResult compile(String entryClassName, String javaSource, Path outputDir);
}
