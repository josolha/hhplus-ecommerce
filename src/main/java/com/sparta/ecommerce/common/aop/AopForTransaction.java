package com.sparta.ecommerce.common.aop;


import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

/**
 * AOP에서 트랜잭션 분리를 위한 클래스
 * 분산 락 해제가 트랜잭션 커밋 이후에 발생하도록 보장
 */
@Component
@Slf4j
public class AopForTransaction {

    @Autowired
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Object proceed(final ProceedingJoinPoint joinPoint) throws Throwable {
        log.info("=== [트랜잭션 확인] 새 트랜잭션 시작 (REQUIRES_NEW) ===");
        log.info("=== [트랜잭션 확인] 현재 스레드: {} ===", Thread.currentThread().getName());

        // TransactionSynchronizationManager로 트랜잭션 정보 확인
        boolean isActualTransactionActive = org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();
        log.info("=== [트랜잭션 확인] 트랜잭션 활성화 여부: {} ===", isActualTransactionActive);

        // DB 커넥션 확인
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            log.info("=== [DB 커넥션 확인] Connection 객체: {} ===", connection);
            log.info("=== [DB 커넥션 확인] Connection 해시코드: {} ===", System.identityHashCode(connection));
        });

        Object result = joinPoint.proceed();

        log.info("=== [트랜잭션 확인] 새 트랜잭션 종료 (곧 커밋됨) ===");

        return result;
    }
}
