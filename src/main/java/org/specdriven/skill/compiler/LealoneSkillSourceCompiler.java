package org.specdriven.skill.compiler;

import com.lealone.db.util.SourceCompiler;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LealoneSkillSourceCompiler implements SkillSourceCompiler {

    private final CompilerBackend compilerBackend;

    public LealoneSkillSourceCompiler() {
        this(new SourceCompilerBackend());
    }

    LealoneSkillSourceCompiler(CompilerBackend compilerBackend) {
        this.compilerBackend = Objects.requireNonNull(compilerBackend, "compilerBackend must not be null");
    }

    @Override
    public SkillCompilationResult compile(String entryClassName, String javaSource, Path outputDir) {
        Objects.requireNonNull(entryClassName, "entryClassName must not be null");
        Objects.requireNonNull(javaSource, "javaSource must not be null");
        Objects.requireNonNull(outputDir, "outputDir must not be null");

        createOutputDirectory(outputDir);
        PreflightCompilationResult preflight = preflight(entryClassName, javaSource);
        if (!preflight.success()) {
            return new SkillCompilationResult(false, entryClassName, preflight.diagnostics());
        }

        try {
            compilerBackend.compile(entryClassName, javaSource, outputDir);
            Path entryClassFile = outputDir.resolve(entryClassName.replace('.', '/') + ".class");
            if (!Files.isRegularFile(entryClassFile)) {
                throw new SkillCompilationException(
                        "Compiled output missing entry class bytes for: " + entryClassName);
            }
            return new SkillCompilationResult(true, entryClassName, List.of());
        } catch (SkillCompilationException e) {
            throw e;
        } catch (Exception e) {
            if (isCompilerUnavailable(e)) {
                throw new SkillCompilationException("Required Lealone compiler capability is unavailable", e);
            }
            throw new SkillCompilationException("Failed to compile skill source for: " + entryClassName, e);
        }
    }

    private void createOutputDirectory(Path outputDir) {
        try {
            Files.createDirectories(outputDir);
        } catch (Exception e) {
            throw new SkillCompilationException("Failed to prepare output directory: " + outputDir.toAbsolutePath(), e);
        }
    }

    private PreflightCompilationResult preflight(String entryClassName, String javaSource) {
        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        if (javaCompiler == null) {
            throw new SkillCompilationException("Required Lealone compiler capability is unavailable: JDK Java compiler not available");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path tempDir = null;
        try (StandardJavaFileManager fileManager = javaCompiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            tempDir = Files.createTempDirectory("skill-source-compiler-");
            List<String> options = new ArrayList<>();
            options.add("-d");
            options.add(tempDir.toString());
            String classPath = System.getProperty("java.class.path");
            if (classPath != null && !classPath.isBlank()) {
                options.add("-classpath");
                options.add(classPath);
            }

            JavaFileObject source = new InMemoryJavaSource(entryClassName, javaSource);
            Boolean success = javaCompiler.getTask(null, fileManager, diagnostics, options, null, List.of(source)).call();
            if (Boolean.TRUE.equals(success)) {
                return new PreflightCompilationResult(true, List.of());
            }
            return new PreflightCompilationResult(false, toDiagnostics(diagnostics));
        } catch (IOException e) {
            throw new SkillCompilationException("Failed to run compilation preflight for: " + entryClassName, e);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static boolean isCompilerUnavailable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("JDK Java compiler not available")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static List<SkillCompilationDiagnostic> toDiagnostics(DiagnosticCollector<JavaFileObject> collector) {
        List<SkillCompilationDiagnostic> diagnostics = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
            if (diagnostic.getKind() != Diagnostic.Kind.ERROR) {
                continue;
            }
            diagnostics.add(new SkillCompilationDiagnostic(
                    diagnostic.getMessage(null),
                    diagnostic.getLineNumber(),
                    diagnostic.getColumnNumber()));
        }
        if (diagnostics.isEmpty()) {
            diagnostics.add(new SkillCompilationDiagnostic("Compilation failed", -1, -1));
        }
        return List.copyOf(diagnostics);
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    interface CompilerBackend {
        void compile(String entryClassName, String javaSource, Path outputDir) throws Exception;
    }

    private static final class SourceCompilerBackend implements CompilerBackend {
        @Override
        public void compile(String entryClassName, String javaSource, Path outputDir) {
            SourceCompiler compiler = new SourceCompiler();
            compiler.setClassDir(outputDir.toFile());
            compiler.setSource(entryClassName, javaSource);
            compiler.compile(entryClassName);
        }
    }

    private record PreflightCompilationResult(boolean success, List<SkillCompilationDiagnostic> diagnostics) {
    }

    private static final class InMemoryJavaSource extends SimpleJavaFileObject {
        private final String source;

        private InMemoryJavaSource(String className, String source) {
            super(URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension), JavaFileObject.Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
