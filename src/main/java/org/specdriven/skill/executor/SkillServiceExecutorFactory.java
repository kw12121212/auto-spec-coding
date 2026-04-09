package org.specdriven.skill.executor;

import com.lealone.db.service.Service;
import com.lealone.db.service.ServiceExecutor;
import com.lealone.db.service.ServiceExecutorFactory;
import com.lealone.db.service.ServiceExecutorFactoryBase;

public class SkillServiceExecutorFactory extends ServiceExecutorFactoryBase implements ServiceExecutorFactory {

    public SkillServiceExecutorFactory() {
        super("skill");
    }

    @Override
    public ServiceExecutor createServiceExecutor(Service service) {
        return new SkillServiceExecutor(service);
    }
}
