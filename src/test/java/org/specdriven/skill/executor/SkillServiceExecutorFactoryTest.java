package org.specdriven.skill.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.lealone.db.service.Service;
import com.lealone.db.service.ServiceExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.specdriven.skill.compiler.LealoneClassCacheManager;
import org.specdriven.skill.compiler.LealoneSkillSourceCompiler;
import org.specdriven.skill.hotload.LealoneSkillHotLoader;
import org.specdriven.skill.hotload.SkillHotLoader;
import org.specdriven.skill.hotload.SkillLoadResult;

import sun.misc.Unsafe;

class SkillServiceExecutorFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void factoryCreatesSkillExecutor() throws Exception {
        SkillServiceExecutorFactory factory = new SkillServiceExecutorFactory();
        ServiceExecutor executor = factory.createServiceExecutor(serviceWithSql(
                "demo",
                "org.specdriven.skill.executor.DemoExecutor",
                "CREATE SERVICE demo LANGUAGE 'skill'"));

        assertEquals("skill", factory.getName());
        assertInstanceOf(SkillServiceExecutor.class, executor);
    }

    @Test
    void factoryPrefersHotLoadedExecutorClass() throws Exception {
        SkillHotLoader hotLoader = new LealoneSkillHotLoader(
                new LealoneSkillSourceCompiler(), new LealoneClassCacheManager(tempDir));
        String executorClassName = "org.specdriven.skill.executor.DemoExecutor";
        String javaSource = """
                package org.specdriven.skill.executor;
                public class DemoExecutor implements com.lealone.db.service.ServiceExecutor {
                    private final com.lealone.db.service.Service service;
                    public DemoExecutor(com.lealone.db.service.Service service) {
                        this.service = service;
                    }
                    public com.lealone.db.service.Service service() { return service; }
                }
                """;
        SkillLoadResult result = hotLoader.load("demo", executorClassName, javaSource, "hash-demo");
        assertTrue(result.success());

        SkillServiceExecutorFactory factory = new SkillServiceExecutorFactory(hotLoader);
        ServiceExecutor executor = factory.createServiceExecutor(serviceWithSql(
                "demo",
                executorClassName,
                "CREATE SERVICE demo LANGUAGE 'skill'"));

        assertEquals(executorClassName, executor.getClass().getName());
        assertSame(hotLoader.activeLoader("demo").orElseThrow(), executor.getClass().getClassLoader());
        assertNotEquals(SkillServiceExecutor.class, executor.getClass());
    }

    @Test
    void factoryFallsBackWhenNoHotLoadedSkillIsActive() throws Exception {
        SkillServiceExecutorFactory factory = new SkillServiceExecutorFactory(new NoOpHotLoader());

        ServiceExecutor executor = factory.createServiceExecutor(serviceWithSql(
                "demo",
                "org.specdriven.skill.executor.DemoExecutor",
                "CREATE SERVICE demo LANGUAGE 'skill'"));

        assertInstanceOf(SkillServiceExecutor.class, executor);
    }

    private static Service serviceWithSql(String name, String implementBy, String sql) throws Exception {
        Unsafe unsafe = unsafe();
        Service service = (Service) unsafe.allocateInstance(Service.class);

        Field nameField = Class.forName("com.lealone.db.DbObjectBase").getDeclaredField("name");
        nameField.setAccessible(true);
        nameField.set(service, name);

        Field sqlField = Service.class.getDeclaredField("sql");
        sqlField.setAccessible(true);
        sqlField.set(service, sql);

        Field implementByField = Service.class.getDeclaredField("implementBy");
        implementByField.setAccessible(true);
        implementByField.set(service, implementBy);

        Field methodsField = Service.class.getDeclaredField("serviceMethods");
        methodsField.setAccessible(true);
        methodsField.set(service, List.of());
        return service;
    }

    private static Unsafe unsafe() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }

    private static final class NoOpHotLoader implements SkillHotLoader {
        @Override
        public SkillLoadResult load(String skillName, String entryClassName, String javaSource, String sourceHash) {
            throw new UnsupportedOperationException("load not used in this test");
        }

        @Override
        public SkillLoadResult replace(String skillName, String entryClassName, String javaSource, String sourceHash) {
            throw new UnsupportedOperationException("replace not used in this test");
        }

        @Override
        public void unload(String skillName) {
        }

        @Override
        public Optional<ClassLoader> activeLoader(String skillName) {
            return Optional.empty();
        }

        @Override
        public Set<String> loadedSkillNames() {
            return Set.of();
        }

        @Override
        public Set<String> failedSkillNames() {
            return Set.of();
        }
    }
}
