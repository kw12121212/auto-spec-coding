package org.specdriven.skill.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.lang.reflect.Field;
import java.util.List;

import com.lealone.db.service.Service;
import com.lealone.db.service.ServiceExecutor;
import org.junit.jupiter.api.Test;

import sun.misc.Unsafe;

class SkillServiceExecutorFactoryTest {

    @Test
    void factoryCreatesSkillExecutor() throws Exception {
        SkillServiceExecutorFactory factory = new SkillServiceExecutorFactory();
        ServiceExecutor executor = factory.createServiceExecutor(serviceWithSql("CREATE SERVICE demo LANGUAGE 'skill'"));

        assertEquals("skill", factory.getName());
        assertInstanceOf(SkillServiceExecutor.class, executor);
    }

    private static Service serviceWithSql(String sql) throws Exception {
        Unsafe unsafe = unsafe();
        Service service = (Service) unsafe.allocateInstance(Service.class);
        Field sqlField = Service.class.getDeclaredField("sql");
        sqlField.setAccessible(true);
        sqlField.set(service, sql);
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
}
