package com.tcore.tcorev2.infrastructure.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 및 Redisson 분산 락 사용을 위한 설정 클래스
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    private static final String REDISSON_HOST_PREFIX = "redis://";

    /**
     * RedissonClient 빈 등록
     * 단일 서버 모드(Single Server) 설정을 사용합니다.
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(REDISSON_HOST_PREFIX + host + ":" + port)
                .setConnectionMinimumIdleSize(5)  // 최소 유휴 커넥션 수
                .setConnectionPoolSize(20);       // 최대 커넥션 풀 크기

        return Redisson.create(config);
    }
}