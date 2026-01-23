package com.sparta.ecommerce.infrastructure.aop.config;


import com.sparta.ecommerce.infrastructure.aop.logtrace.LogTrace;
import com.sparta.ecommerce.infrastructure.aop.logtrace.LogTraceAspect;
import com.sparta.ecommerce.infrastructure.aop.logtrace.ThreadLocalLogTrace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AopConfig {

    @Bean
    public LogTraceAspect logTraceAspect(LogTrace logTrace) {
        return new LogTraceAspect(logTrace);
    }

    @Bean
    public LogTrace logTrace() {
        return new ThreadLocalLogTrace();
    }
}
