package com.sparta.ecommerce.infrastructure.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 분산 락 설정
 * Redis 기반의 분산 락을 사용하기 위한 RedissonClient 빈을 생성
 */
@Configuration
public class RedissonConfig {

    // Redis 프로토콜 프리픽스
    private static final String REDIS_PROTOCOL_PREFIX = "redis://";

    // 커넥션 풀 설정값
    private static final int CONNECTION_MINIMUM_IDLE_SIZE = 10;
    private static final int CONNECTION_POOL_SIZE = 20;
    private static final int IDLE_CONNECTION_TIMEOUT = 10000;
    private static final int CONNECT_TIMEOUT = 3000;
    private static final int COMMAND_TIMEOUT = 3000;

    // application.yml에서 Redis 호스트 주소를 주입받음
    @Value("${spring.data.redis.host}")
    private String redisHost;

    // application.yml에서 Redis 포트 번호를 주입받음
    @Value("${spring.data.redis.port}")
    private int redisPort;

    /**
     * RedissonClient 빈 생성
     * 분산 락, 분산 객체, 분산 컬렉션 등을 사용하기 위한 Redisson 클라이언트
     */
    @Bean
    public RedissonClient redissonClient() {
        // Redisson 설정 객체 생성
        Config config = new Config();

        // 단일 Redis 서버 모드 설정 (클러스터가 아닌 standalone 모드)
        config.useSingleServer()
                // Redis 서버 주소 설정 (redis://host:port 형식)
                .setAddress(REDIS_PROTOCOL_PREFIX + redisHost + ":" + redisPort)

                // 최소 유휴 커넥션 수: 항상 유지할 최소 연결 개수 (성능 최적화)
                .setConnectionMinimumIdleSize(CONNECTION_MINIMUM_IDLE_SIZE)

                // 커넥션 풀 크기: 최대 생성 가능한 연결 개수
                .setConnectionPoolSize(CONNECTION_POOL_SIZE)

                // 유휴 커넥션 타임아웃: 사용되지 않는 연결을 유지하는 시간 (밀리초)
                // 10000ms = 10초 동안 사용되지 않으면 연결 해제
                .setIdleConnectionTimeout(IDLE_CONNECTION_TIMEOUT)

                // 연결 타임아웃: Redis 서버에 연결을 시도하는 최대 시간 (밀리초)
                // 3000ms = 3초 이내에 연결되지 않으면 실패
                .setConnectTimeout(CONNECT_TIMEOUT)

                // 명령 실행 타임아웃: Redis 명령어 응답을 기다리는 최대 시간 (밀리초)
                // 3000ms = 3초 이내에 응답이 없으면 실패
                .setTimeout(COMMAND_TIMEOUT);

        // 설정을 기반으로 RedissonClient 인스턴스 생성 및 반환
        return Redisson.create(config);
    }
}
