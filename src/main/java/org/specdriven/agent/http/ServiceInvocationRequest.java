package org.specdriven.agent.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record ServiceInvocationRequest(List<Object> args) {

    public ServiceInvocationRequest {
        Objects.requireNonNull(args, "args must not be null");
        args = Collections.unmodifiableList(new ArrayList<>(args));
    }
}
