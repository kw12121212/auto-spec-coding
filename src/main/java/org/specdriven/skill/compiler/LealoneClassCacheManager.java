package org.specdriven.skill.compiler;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

public final class LealoneClassCacheManager implements ClassCacheManager {

    private final Path baseDir;

    public LealoneClassCacheManager(Path baseDir) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir must not be null");
    }

    @Override
    public boolean isCached(String skillName, String sourceHash) {
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(sourceHash, "sourceHash must not be null");
        Path slotDir = slotPath(skillName, sourceHash);
        if (!Files.isDirectory(slotDir)) {
            return false;
        }
        try (Stream<Path> entries = Files.walk(slotDir)) {
            return entries.anyMatch(p -> p.toString().endsWith(".class") && Files.isRegularFile(p));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Path resolveClassDir(String skillName, String sourceHash) {
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(sourceHash, "sourceHash must not be null");
        Path slotDir = slotPath(skillName, sourceHash);
        try {
            Files.createDirectories(slotDir);
        } catch (IOException e) {
            throw new ClassCacheException(
                    "Failed to create cache directory: " + slotDir.toAbsolutePath(), e);
        }
        return slotDir;
    }

    @Override
    public ClassLoader loadCached(String skillName, String sourceHash) {
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(sourceHash, "sourceHash must not be null");
        if (!isCached(skillName, sourceHash)) {
            throw new ClassCacheException(
                    "No cached classes found for skill '" + skillName + "' with hash '" + sourceHash + "'");
        }
        Path slotDir = slotPath(skillName, sourceHash);
        try {
            URL[] urls = {slotDir.toUri().toURL()};
            return new java.net.URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
        } catch (IOException e) {
            throw new ClassCacheException(
                    "Failed to create ClassLoader for cached skill '" + skillName + "'", e);
        }
    }

    @Override
    public void invalidate(String skillName, String sourceHash) {
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(sourceHash, "sourceHash must not be null");
        Path slotDir = slotPath(skillName, sourceHash);
        if (!Files.exists(slotDir)) {
            return;
        }
        try {
            deleteRecursively(slotDir);
        } catch (IOException e) {
            throw new ClassCacheException(
                    "Failed to invalidate cache slot for skill '" + skillName + "'", e);
        }
    }

    private Path slotPath(String skillName, String sourceHash) {
        return baseDir.resolve(skillName).resolve(sourceHash);
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
