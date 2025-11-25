package com.sparta.ecommerce.common.aop.logtrace;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ThreadLocalLogTrace implements LogTrace {

    private static final String START_PREFIX = "-->";
    private static final String COMPLETE_PREFIX = "<--";
    private static final String EX_PREFIX = "<X-";

    @Override
    public TraceStatus begin(String message) {
        syncTraceId();
        TraceId traceId = TraceIdHolder.getId(); // TraceIdHolder를 통해 현재 TraceId를 가져옵니다.
        Long startTimeMs = System.currentTimeMillis();
        log.info("[{}] {}{}", traceId.getId(), addSpace(START_PREFIX, traceId.getLevel()), message);

        return new TraceStatus(traceId, startTimeMs, message);
    }

    @Override
    public void end(TraceStatus status) {
        complete(status, null);
    }

    @Override
    public void exception(TraceStatus status, Exception e) {
        complete(status, e);
    }

    private void complete(TraceStatus status, Exception e) {
        Long stopTimeMs = System.currentTimeMillis();
        long resultTimeMs = stopTimeMs - status.getStartTimeMs();
        TraceId traceId = status.getTraceId();
        if (e == null) {
            log.info("[{}] {}{} time={}ms", traceId.getId(), addSpace(COMPLETE_PREFIX, traceId.getLevel()), status.getMessage(), resultTimeMs);
        } else {
            log.info("[{}] {}{} time={}ms ex={}", traceId.getId(), addSpace(EX_PREFIX, traceId.getLevel()), status.getMessage(), resultTimeMs, e.toString());
        }

        releaseTraceId();
    }

    private void syncTraceId() {
        TraceId currentTraceId = TraceIdHolder.getId();
        if (currentTraceId == null) {
            TraceIdHolder.set(new TraceId());
        } else {
            TraceIdHolder.set(currentTraceId.createNextId());
        }
    }

    private void releaseTraceId() {
        TraceId currentTraceId = TraceIdHolder.getId();
        if (currentTraceId != null && currentTraceId.isFirstLevel()) {
            TraceIdHolder.remove();
        } else if (currentTraceId != null) {
            TraceIdHolder.set(currentTraceId.createPreviousId());
        }
    }

    private String addSpace(String prefix, int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append((i == level - 1) ? "|" + prefix : "|   ");
        }
        return sb.toString();
    }
}