package com.tcore.tcorev2.application.ai;

import com.tcore.tcorev2.infrastructure.ai.TrafficControlTools;
import com.tcore.tcorev2.infrastructure.monitoring.MockSystemMetricAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시스템 부하에 따라 대기열의 유저 입장 속도를 제어하는 AI 에이전트.
 * 현재는 Mock 데이터를 기반으로 동작하며, 주기적으로 시스템 상태를 확인하고
 * 사전 정의된 규칙에 따라 대기열을 제어하는 로직을 시뮬레이션합니다.
 * <p>
 * AI가 Tools를 호출하는 방식(Function Calling)은 Spring AI를 통해 이루어지지만,
 * Mock ChatModel 환경에서는 AI의 '판단' 부분을 Rule-based로 대체하여 구현합니다.
 */
@Slf4j
@Component
@Profile({"dev", "test"}) // 개발 및 테스트 프로파일에서만 동작
@RequiredArgsConstructor
public class TrafficControlAgent {

    private final TrafficControlTools trafficControlTools;
    private final MockSystemMetricAdapter mockSystemMetricAdapter; // Mock 시뮬레이션을 위해 주입

    private static final long CONCERT_ID = 1L; // 대상 콘서트 ID (예시)
    private static final double HIGH_CPU_THRESHOLD = 70.0; // CPU 고부하 임계치
    private static final double LOW_CPU_THRESHOLD = 30.0;  // CPU 저부하 임계치
    private static final int DEFAULT_ACTIVATION_COUNT = 10; // 기본 활성화 유저 수

    /**
     * 5초마다 시스템 상태를 확인하고 대기열을 제어합니다.
     */
    @Scheduled(fixedRate = 5000) // 5초마다 실행
    public void controlTraffic() {
        log.info("TrafficControlAgent: Checking system status and queue...");

        // 1. 시스템 메트릭 조회 (Tools 사용)
        TrafficControlTools.SystemLoadResponse systemLoad = trafficControlTools.getSystemLoad().apply(new TrafficControlTools.SystemLoadRequest("agent"));
        TrafficControlTools.QueueMetricsResponse queueMetrics = trafficControlTools.getQueueMetrics().apply(new TrafficControlTools.QueueMetricsRequest(CONCERT_ID));

        double cpuUsage = systemLoad.cpuUsage();
        long waitingSize = queueMetrics.waitingSize();
        long activeCount = queueMetrics.activeUserCount();

        log.info("Current System Metrics - CPU: {}%, Waiting: {}, Active: {}", cpuUsage, waitingSize, activeCount);

        // 2. AI의 판단 로직 (Mock 환경에서는 Rule-based로 대체)
        int activateCount = 0;
        if (cpuUsage < LOW_CPU_THRESHOLD) {
            // CPU가 낮으면 적극적으로 유저 활성화
            activateCount = DEFAULT_ACTIVATION_COUNT * 2; // 두 배로 활성화
            log.info("CPU usage is low. Activating {} users.", activateCount);
        } else if (cpuUsage > HIGH_CPU_THRESHOLD) {
            // CPU가 높으면 유저 활성화 중단 또는 감소
            activateCount = DEFAULT_ACTIVATION_COUNT / 2; // 절반만 활성화
            log.warn("CPU usage is high! Reducing activation count to {}.", activateCount);
        } else {
            // 정상 범위면 기본 활성화
            activateCount = DEFAULT_ACTIVATION_COUNT;
            log.info("CPU usage is normal. Activating {} users.", activateCount);
        }

        // 대기열에 유저가 없거나 활성화할 유저가 없으면 skip
        if (waitingSize == 0 || activateCount <= 0) {
            log.info("No users to activate or activation count is zero.");
            return;
        }
        
        // 3. Tools를 사용하여 유저 활성화 (AI의 결정 실행)
        TrafficControlTools.ActivateUsersResponse activationResult = trafficControlTools.activateUsers().apply(
                new TrafficControlTools.ActivateUsersRequest(CONCERT_ID, activateCount)
        );

        if (activationResult.success()) {
            log.info("Users activation successful: {}", activationResult.message());
        } else {
            log.error("Users activation failed: {}", activationResult.message());
        }
    }
}
