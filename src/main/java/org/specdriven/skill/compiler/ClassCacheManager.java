package org.specdriven.skill.compiler;

import java.nio.file.Path;

public interface ClassCacheManager {

    boolean isCached(String skillName, String sourceHash);

    Path resolveClassDir(String skillName, String sourceHash);

    ClassLoader loadCached(String skillName, String sourceHash);

    void invalidate(String skillName, String sourceHash);
}
