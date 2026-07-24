package com.jobseekercopilot.jobservice.service;

import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.MDC;

final class DownstreamTaskContext {

    private DownstreamTaskContext() {
    }

    static <T> Callable<T> withCurrentMdc(Callable<T> task) {
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (captured == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(captured);
                }
                return task.call();
            } finally {
                if (previous == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        };
    }
}
