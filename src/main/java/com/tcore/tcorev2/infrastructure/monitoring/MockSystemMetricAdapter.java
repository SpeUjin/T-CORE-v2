package com.tcore.tcorev2.infrastructure.monitoring;

import com.tcore.tcorev2.application.port.SystemMetricPort;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 시스템 메트릭을 시뮬레이션하는 Mock 구현체입니다.
 * 실제 시스템 지표 대신 테스트 및 개발 시나리오를 위해 값을 조작할 수 있습니다.
 */
@Slf4j
@Component
@Profile({"dev", "test"}) // 개발 및 테스트 프로파일에서 활성화
@Getter // 테스트 코드에서 값을 설정하기 위함
@Setter // 테스트 코드에서 값을 설정하기 위함
public class MockSystemMetricAdapter implements SystemMetricPort {

    private double cpuUsage = 20.0; // 기본 CPU 사용률 (낮은 부하)
    private double memoryUsage = 50.0; // 기본 메모리 사용률

    // 콘서트별 대기열 및 활성 유저 수 (시뮬레이션을 위해 Map으로 관리)
    private Map<Long, Long> waitingQueueSizes = new HashMap<>();
    private Map<Long, Long> activeUserCounts = new HashMap<>();

    @Override
    public double getCpuUsage() {
        log.debug("Mock CPU Usage: {}%", cpuUsage);
        return cpuUsage;
    }

    @Override
    public double getMemoryUsage() {
        log.debug("Mock Memory Usage: {}%", memoryUsage);
        return memoryUsage;
    }

    @Override
    public long getWaitingQueueSize(Long concertId) {
        long size = waitingQueueSizes.getOrDefault(concertId, 0L);
        log.debug("Mock Waiting Queue Size for concert {}: {}", concertId, size);
        return size;
    }

    @Override
    public long getActiveUserCount(Long concertId) {
        long count = activeUserCounts.getOrDefault(concertId, 0L);
        log.debug("Mock Active User Count for concert {}: {}", concertId, count);
        return count;
    }

    // 테스트 및 시뮬레이션을 위한 값 설정 메서드들 (Setter 역할)
    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public void setMemoryUsage(double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public void setWaitingQueueSize(Long concertId, long size) {
        waitingQueueSizes.put(concertId, size);
    }

    public void setActiveUserCount(Long concertId, long count) {
        activeUserCounts.put(concertId, count);
    }

    public void reset() {
        this.cpuUsage = 20.0;
        this.memoryUsage = 50.0;
        this.waitingQueueSizes.clear();
        this.activeUserCounts.clear();
    }
}
