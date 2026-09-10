package com.dream.inventory.common;

import java.util.UUID;

public final class TraceContext {

    private static final ThreadLocal<String> TRACE = new ThreadLocal<>();

    private TraceContext() {
    }

    public static String getOrCreate() {
        String trace = TRACE.get();
        if (trace == null) {
            trace = UUID.randomUUID().toString().replace("-", "");
            TRACE.set(trace);
        }
        return trace;
    }

    public static void set(String traceId) {
        TRACE.set(traceId);
    }

    public static void clear() {
        TRACE.remove();
    }
}
