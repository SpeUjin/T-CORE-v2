package com.tcore.tcorev2.infrastructure.monitoring;

import com.tcore.tcorev2.application.port.SystemMetricPort;
import com.tcore.tcorev2.application.service.RedisWaitingRoomService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

@Slf4j
@Component // ⭐️ 드디어 빈으로 등록됩니다!
@RequiredArgsConstructor
public class ActuatorMetricAdapter implements SystemMetricPort {

    private final MeterRegistry meterRegistry;
    private final RedisWaitingRoomService waitingRoomService;

    @Override
    public double getCpuUsage() {
        // OS 레벨의 CPU 사용량을 가져옵니다.
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        double load = osBean.getCpuLoad();

        // 데이터가 아직 수집되지 않았을 때는 음수가 나올 수 있으므로 0으로 보정
        return load < 0 ? 0.0 : load * 100.0;
    }

    @Override
    public int getActiveDbConnections() {
        try {
            // HikariCP 커넥션 풀의 현재 활성(Active) 커넥션 수를 가져옵니다.
            return (int) meterRegistry.get("hikaricp.connections.active").gauge().value();
        } catch (Exception e) {
            log.warn("DB 커넥션 지표를 아직 수집할 수 없습니다.");
            return 0; // 아직 쿼리가 발생하지 않아 지표가 없을 때의 방어 로직
        }
    }

    @Override
    public long getWaitingQueueSize(Long concertId) {
        // 우리가 Phase 4에서 만든 Redis 대기열 사이즈 조회 메서드 호출
        return waitingRoomService.getWaitingQueueSize(concertId);
    }
}