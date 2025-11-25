package com.sparta.ecommerce.common.aop.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class TraceAspect {

    @Before("@annotation(com.sparta.ecommerce.common.aop.annotation.Trace)")
    public void doTrace(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        log.info("==============================[EXECU]{}==============================",joinPoint.getSignature().toShortString());
        log.info("ARGS={}",args);
        log.info("==============================[END]{}==============================",joinPoint.getSignature().toShortString());
    }
}
