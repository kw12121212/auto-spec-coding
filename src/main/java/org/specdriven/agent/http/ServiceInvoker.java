package org.specdriven.agent.http;

import java.util.List;

@FunctionalInterface
public interface ServiceInvoker {

    Object invoke(String serviceName, String methodName, List<Object> args);
}
