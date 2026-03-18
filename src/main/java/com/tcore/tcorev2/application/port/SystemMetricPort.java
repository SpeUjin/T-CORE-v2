package com.tcore.tcorev2.application.port;

public interface SystemMetricPort {
    /** 현재 서버의 CPU 사용량을 반환합니다. (0.0 ~ 100.0%) */
    double getCpuUsage();

    /** 현재 사용 중인 DB 커넥션(HikariCP) 개수를 반환합니다. */
    int getActiveDbConnections();

    /** 현재 Redis 대기열에서 기다리고 있는 총 인원수를 반환합니다. */
    long getWaitingQueueSize(Long concertId);
}