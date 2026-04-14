package org.specdriven.skill.executor;

import com.lealone.db.service.Service;
import com.lealone.db.service.ServiceExecutor;
import com.lealone.db.service.ServiceExecutorFactory;
import com.lealone.db.service.ServiceExecutorFactoryBase;
import org.specdriven.skill.hotload.SkillHotLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

public class SkillServiceExecutorFactory extends ServiceExecutorFactoryBase implements ServiceExecutorFactory {

    private final SkillHotLoader hotLoader;

    public SkillServiceExecutorFactory() {
        this(null);
    }

    public SkillServiceExecutorFactory(SkillHotLoader hotLoader) {
        super("skill");
        this.hotLoader = hotLoader;
    }

    @Override
    public ServiceExecutor createServiceExecutor(Service service) {
        Objects.requireNonNull(service, "service must not be null");
        if (hotLoader != null) {
            String skillName = service.getName();
            if (skillName != null) {
                return hotLoader.activeLoader(skillName)
                        .map(loader -> instantiateHotLoadedExecutor(loader, service))
                        .orElseGet(() -> new SkillServiceExecutor(service));
            }
        }
        return new SkillServiceExecutor(service);
    }

    private ServiceExecutor instantiateHotLoadedExecutor(ClassLoader loader, Service service) {
        try {
            Class<?> executorClass = Class.forName(service.getImplementBy(), true, loader);
            if (!ServiceExecutor.class.isAssignableFrom(executorClass)) {
                throw new IllegalStateException("Hot-loaded executor does not implement ServiceExecutor: "
                        + service.getImplementBy());
            }
            Constructor<?> constructor = executorClass.getDeclaredConstructor(Service.class);
            constructor.setAccessible(true);
            return (ServiceExecutor) constructor.newInstance(service);
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                 | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to instantiate hot-loaded executor: "
                    + service.getImplementBy(), e);
        }
    }
}
