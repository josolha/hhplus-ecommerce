package com.sparta.ecommerce.common.aop.logtrace;

public class TraceIdHolder {
    private static final ThreadLocal<TraceId> traceIdHolder = new ThreadLocal<>();

    public static void set(TraceId traceId) {
        traceIdHolder.set(traceId);
    }

    public static TraceId getId() {
        return traceIdHolder.get();
    }

    public static void remove() {
        traceIdHolder.remove();
    }
}

