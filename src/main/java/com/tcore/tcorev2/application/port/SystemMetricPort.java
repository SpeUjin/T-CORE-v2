package com.tcore.tcorev2.application.port;

public interface SystemMetricPort {
    /**
     * 현재 시스템의 CPU 사용률을 반환합니다.
     * @return CPU 사용률 (0.0 ~ 100.0)
     */
    double getCpuUsage();

    /**
     * 현재 시스템의 메모리 사용률을 반환합니다.
     * @return 메모리 사용률 (0.0 ~ 100.0)
     */
    double getMemoryUsage();

    /**
     * 특정 콘서트의 대기열에 있는 유저 수를 반환합니다.
     * @param concertId 콘서트 ID
     * @return 대기열 유저 수
     */
    long getWaitingQueueSize(Long concertId);

    /**
     * 특정 콘서트의 활성 유저(예약 페이지 입장 허용된) 수를 반환합니다.
     * @param concertId 콘서트 ID
     * @return 활성 유저 수
     */
    long getActiveUserCount(Long concertId);
}
